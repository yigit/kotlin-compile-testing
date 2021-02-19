package com.tschuchort.compiletesting

import org.jetbrains.kotlin.backend.jvm.jvmPhases
import org.jetbrains.kotlin.cli.common.*
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.extensions.ScriptEvaluationExtension
import org.jetbrains.kotlin.cli.common.extensions.ShellExtension
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.*
import org.jetbrains.kotlin.cli.common.messages.FilteringMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.cli.common.messages.OutputMessageUtil
import org.jetbrains.kotlin.cli.common.modules.ModuleBuilder
import org.jetbrains.kotlin.cli.common.modules.ModuleChunk
import org.jetbrains.kotlin.cli.common.profiling.ProfilingCompilerPerformanceManager
import org.jetbrains.kotlin.cli.jvm.*
import org.jetbrains.kotlin.cli.jvm.compiler.CompileEnvironmentUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.load.java.JavaClassesTracker
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCompilationComponents
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmMetadataVersion
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.modules.JavaRootPath
import org.jetbrains.kotlin.utils.KotlinPaths
import java.io.File

object KCTTimer {

    private var start = now()

    private val recordings = mutableListOf<Pair<String, Long>>()
    fun reset() {
        recordings.clear()
        start = now()
    }
    fun record(name: String) {
        //recordings.add(name to now())
    }

    fun dump() = buildString {
        val end = now()
        appendln("total time: ${end - start}")
        recordings.fold(start) { acc, next ->
            appendln("${next.first}: ${next.second - acc}")
            next.second
        }
    }

    private inline fun now() = System.currentTimeMillis()
}

class FastKotlinCompiler : CLICompiler<K2JVMCompilerArguments>(){
    override val performanceManager: CommonCompilerPerformanceManager
        get() = error("unsupported")

    override fun createMetadataVersion(versionArray: IntArray): BinaryVersion {
        return JvmMetadataVersion(*versionArray)
    }

    override fun doExecute(
        arguments: K2JVMCompilerArguments,
        configuration: CompilerConfiguration,
        rootDisposable: Disposable,
        paths: KotlinPaths?
    ): ExitCode {
        return _doExecute(KCTTimer, arguments, configuration, rootDisposable, paths)
    }
    private fun _doExecute(
        timer:KCTTimer,
        arguments: K2JVMCompilerArguments,
        configuration: CompilerConfiguration,
        rootDisposable: Disposable,
        paths: KotlinPaths?
    ): ExitCode {
        timer.record("begin")
        val messageCollector = configuration.getNotNull(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY)

        configuration.putIfNotNull(CLIConfigurationKeys.REPEAT_COMPILE_MODULES, arguments.repeatCompileModules?.toIntOrNull())
        configuration.put(CLIConfigurationKeys.PHASE_CONFIG, createPhaseConfig(jvmPhases, arguments, messageCollector))

        if (!configuration.configureJdkHome(arguments)) return ExitCode.COMPILATION_ERROR

        configuration.put(JVMConfigurationKeys.DISABLE_STANDARD_SCRIPT_DEFINITION, arguments.disableStandardScript)
        timer.record("done jvm config")
        val pluginLoadResult = loadPlugins(paths, arguments, configuration)
        timer.record("loaded plugins")
        if (pluginLoadResult != ExitCode.OK) return pluginLoadResult

        val moduleName = arguments.moduleName ?: JvmProtoBufUtil.DEFAULT_MODULE_NAME
        configuration.put(CommonConfigurationKeys.MODULE_NAME, moduleName)

        configuration.configureExplicitContentRoots(arguments)
        timer.record("configure content roots")
        configuration.configureStandardLibs(paths, arguments)
        timer.record("configure standard libs")
        configuration.configureAdvancedJvmOptions(arguments)
        timer.record("configure jvm options")
        configuration.configureKlibPaths(arguments)
        timer.record("configure klib paths")

        if (arguments.buildFile == null && !arguments.version  && !arguments.allowNoSourceFiles &&
            (arguments.script || arguments.expression != null || arguments.freeArgs.isEmpty())) {

            // script or repl
            if (arguments.script && arguments.freeArgs.isEmpty()) {
                messageCollector.report(ERROR, "Specify script source path to evaluate")
                return ExitCode.COMPILATION_ERROR
            }

            val projectEnvironment =
                KotlinCoreEnvironment.ProjectEnvironment(
                    rootDisposable,
                    KotlinCoreEnvironment.getOrCreateApplicationEnvironmentForProduction(rootDisposable, configuration)
                )
            timer.record("build project env")
            projectEnvironment.registerExtensionsFromPlugins(configuration)
            timer.record("registered extensions from plugins")

            if (arguments.script || arguments.expression != null) {
                val scriptingEvaluator = ScriptEvaluationExtension.getInstances(projectEnvironment.project).find { it.isAccepted(arguments) }
                if (scriptingEvaluator == null) {
                    messageCollector.report(ERROR, "Unable to evaluate script, no scripting plugin loaded")
                    return ExitCode.COMPILATION_ERROR
                }
                return scriptingEvaluator.eval(arguments, configuration, projectEnvironment)
            } else {
                val shell = ShellExtension.getInstances(projectEnvironment.project).find { it.isAccepted(arguments) }
                if (shell == null) {
                    messageCollector.report(ERROR, "Unable to run REPL, no scripting plugin loaded")
                    return ExitCode.COMPILATION_ERROR
                }
                return shell.run(arguments, configuration, projectEnvironment)
            }
        }

        messageCollector.report(LOGGING, "Configuring the compilation environment")
        try {
            timer.record("start building modules")
            val destination = arguments.destination?.let { File(it) }
            val buildFile = arguments.buildFile?.let { File(it) }

            val moduleChunk = if (buildFile != null) {
                fun strongWarning(message: String) {
                    messageCollector.report(STRONG_WARNING, message)
                }
                if (destination != null) {
                    strongWarning("The '-d' option with a directory destination is ignored because '-Xbuild-file' is specified")
                }
                if (arguments.javaSourceRoots != null) {
                    strongWarning("The '-Xjava-source-roots' option is ignored because '-Xbuild-file' is specified")
                }
                if (arguments.javaPackagePrefix != null) {
                    strongWarning("The '-Xjava-package-prefix' option is ignored because '-Xbuild-file' is specified")
                }

                val sanitizedCollector = FilteringMessageCollector(messageCollector, VERBOSE::contains)
                configuration.put(JVMConfigurationKeys.MODULE_XML_FILE, buildFile)
                CompileEnvironmentUtil.loadModuleChunk(buildFile, sanitizedCollector)
            } else {
                if (destination != null) {
                    if (destination.path.endsWith(".jar")) {
                        configuration.put(JVMConfigurationKeys.OUTPUT_JAR, destination)
                    } else {
                        configuration.put(JVMConfigurationKeys.OUTPUT_DIRECTORY, destination)
                    }
                }

                val module = ModuleBuilder(moduleName, destination?.path ?: ".", "java-production")
                module.configureFromArgs(arguments)

                ModuleChunk(listOf(module))
            }

            val chunk = moduleChunk.modules
            // TODO https://github.com/JetBrains/kotlin/blob/master/compiler/cli/src/org/jetbrains/kotlin/cli/jvm/compiler/KotlinToJVMBytecodeCompiler.kt#L253
            timer.record("modules ready")
            KotlinToJVMBytecodeCompiler.pleaseConfigureSourceRoots(configuration, chunk, buildFile)
            timer.record("source roots reads")

            val environment = createCoreEnvironment(
                rootDisposable, configuration, messageCollector,
                chunk.map { input -> input.getModuleName() + "-" + input.getModuleType() }.let { names ->
                    names.singleOrNull() ?: names.joinToString()
                }
            ) ?: return ExitCode.COMPILATION_ERROR
            val end = System.currentTimeMillis()
            timer.record("env ready")
            environment.registerJavacIfNeeded(arguments).let {
                if (!it) return ExitCode.COMPILATION_ERROR
            }

            if (environment.getSourceFiles().isEmpty() && !arguments.allowNoSourceFiles && buildFile == null) {
                if (arguments.version) return ExitCode.OK

                messageCollector.report(ERROR, "No source files")
                return ExitCode.COMPILATION_ERROR
            }
timer.record("pre compilation")
            KotlinToJVMBytecodeCompiler.pleaseCompileModules(environment, buildFile, chunk)
            timer.record("done compilation")
            return ExitCode.OK
        } catch (e: CompilationException) {
            messageCollector.report(
                EXCEPTION,
                OutputMessageUtil.renderException(e),
                MessageUtil.psiElementToMessageLocation(e.element)
            )
            return ExitCode.INTERNAL_ERROR
        } catch (th: Throwable) {
            println("what is this? ${th}")
            return ExitCode.INTERNAL_ERROR
        }
    }

    private fun ModuleBuilder.configureFromArgs(args: K2JVMCompilerArguments) {
        args.friendPaths?.forEach { addFriendDir(it) }
        args.classpath?.split(File.pathSeparator)?.forEach { addClasspathEntry(it) }
        args.javaSourceRoots?.forEach {
            addJavaSourceRoot(JavaRootPath(it, args.javaPackagePrefix))
        }

        val commonSources = args.commonSources?.toSet().orEmpty()
        for (arg in args.freeArgs) {
            if (arg.endsWith(".java")) {
                addJavaSourceRoot(JavaRootPath(arg, args.javaPackagePrefix))
            } else {
                addSourceFiles(arg)
                if (arg in commonSources) {
                    addCommonSourceFiles(arg)
                }

                if (File(arg).isDirectory) {
                    addJavaSourceRoot(JavaRootPath(arg, args.javaPackagePrefix))
                }
            }
        }
    }

    private fun createCoreEnvironment(
        rootDisposable: Disposable,
        configuration: CompilerConfiguration,
        messageCollector: MessageCollector,
        targetDescription: String
    ): KotlinCoreEnvironment? {
        System.setProperty(KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY, "true")
        if (messageCollector.hasErrors()) return null

        val environment = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        // createForTests is slower???
//        val environment = KotlinCoreEnvironment.createForTests(
//            rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
//        )

        val sourceFiles = environment.getSourceFiles()
//        configuration[CLIConfigurationKeys.PERF_MANAGER]?.notifyCompilerInitialized(
//            sourceFiles.size, environment.countLinesOfCode(sourceFiles), targetDescription
//        )

        return if (messageCollector.hasErrors()) null else environment
    }


    override fun setupPlatformSpecificArgumentsAndServices(
        configuration: CompilerConfiguration,
        arguments: K2JVMCompilerArguments,
        services: Services
    ) {
        with(configuration) {
            if (IncrementalCompilation.isEnabledForJvm()) {
                putIfNotNull(CommonConfigurationKeys.LOOKUP_TRACKER, services[LookupTracker::class.java])

                putIfNotNull(CommonConfigurationKeys.EXPECT_ACTUAL_TRACKER, services[ExpectActualTracker::class.java])

                putIfNotNull(
                    JVMConfigurationKeys.INCREMENTAL_COMPILATION_COMPONENTS,
                    services[IncrementalCompilationComponents::class.java]
                )

                putIfNotNull(JVMConfigurationKeys.JAVA_CLASSES_TRACKER, services[JavaClassesTracker::class.java])
            }
            setupJvmSpecificArguments(arguments)
        }
    }

    override fun MutableList<String>.addPlatformOptions(arguments: K2JVMCompilerArguments) {
        if (arguments.scriptTemplates?.isNotEmpty() == true) {
            add("plugin:kotlin.scripting:script-templates=${arguments.scriptTemplates!!.joinToString(",")}")
        }
        if (arguments.scriptResolverEnvironment?.isNotEmpty() == true) {
            add(
                "plugin:kotlin.scripting:script-resolver-environment=${arguments.scriptResolverEnvironment!!.joinToString(
                    ","
                )}"
            )
        }
    }

    override fun createArguments(): K2JVMCompilerArguments = K2JVMCompilerArguments().apply {
        if (System.getenv("KOTLIN_REPORT_PERF") != null) {
            reportPerf = true
        }
    }

    override fun executableScriptFileName() = "kotlinc-jvm"
    protected class FastCompilerPerformanceManager : CommonCompilerPerformanceManager("Kotlin to JVM Compiler")
    override fun createPerformanceManager(arguments: K2JVMCompilerArguments, services: Services): CommonCompilerPerformanceManager {
        val externalManager = services[CommonCompilerPerformanceManager::class.java]
        if (externalManager != null) return externalManager
        val argument = arguments.profileCompilerCommand ?: return FastCompilerPerformanceManager()
        return ProfilingCompilerPerformanceManager.create(argument)
    }
}

