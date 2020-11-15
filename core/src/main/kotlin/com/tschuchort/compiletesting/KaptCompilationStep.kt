package com.tschuchort.compiletesting

import org.jetbrains.kotlin.base.kapt3.AptMode
import org.jetbrains.kotlin.base.kapt3.KaptFlag
import org.jetbrains.kotlin.base.kapt3.KaptOptions
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.kapt3.base.incremental.DeclaredProcType
import org.jetbrains.kotlin.kapt3.base.incremental.IncrementalProcessor
import org.jetbrains.kotlin.kapt3.util.MessageCollectorBackedKaptLogger
import java.io.File
import java.net.URLClassLoader

internal class KaptCompilationStep : CompilationStep<KotlinCompilation> {
    override val order: CompilationStep.Order
        get() = CompilationStep.Order.PRE_KOTLIN_COMPILATION

    override fun execute(
        compilation: KotlinCompilation,
        sourceFiles: List<File>
    ): CompilationStep.IntermediateResult {
        val kaptModel = compilation.kapt
        val model = compilation.model
        if (kaptModel.annotationProcessors.isEmpty()) {
            return CompilationStep.IntermediateResult.SKIP
        }
        return try {
            /* Work around for warning that sometimes happens:
            "Failed to initialize native filesystem for Windows
            java.lang.RuntimeException: Could not find installation home path.
            Please make sure bin/idea.properties is present in the installation directory"
            See: https://github.com/arturbosch/detekt/issues/630
            */
            withSystemProperty("idea.use.native.fs.for.win", "false") {
                runKapt(kaptModel, model, sourceFiles, compilation)
            }
        } finally {
            MainComponentRegistrar.threadLocalParameters.remove()
        }
    }

    private fun runKapt(
        kaptModel: KaptModel,
        model: CompilationModelImpl.JvmCompilationModelImpl,
        sourceFiles: List<File>,
        compilation: KotlinCompilation
    ): CompilationStep.IntermediateResult {
        kaptModel.sourceDir.mkdirs()
        kaptModel.stubsDir.mkdirs()
        kaptModel.incrementalDataDir.mkdirs()
        kaptModel.kotlinGeneratedDir.mkdirs()

        val kaptOptions = KaptOptions.Builder().also {
            it.stubsOutputDir = kaptModel.stubsDir
            it.sourcesOutputDir = kaptModel.sourceDir
            it.incrementalDataOutputDir = kaptModel.incrementalDataDir
            it.classesOutputDir = model.classesDir
            it.processingOptions.apply {
                putAll(kaptModel.args)
                putIfAbsent(KotlinCompilation.OPTION_KAPT_KOTLIN_GENERATED, kaptModel.kotlinGeneratedDir.absolutePath)
            }

            it.mode = AptMode.STUBS_AND_APT

            if (model.verbose)
                it.flags.addAll(KaptFlag.MAP_DIAGNOSTIC_LOCATIONS, KaptFlag.VERBOSE)
        }

        val compilerMessageCollector = PrintingMessageCollector(
            model.internalMessageStream, MessageRenderer.GRADLE_STYLE, model.verbose
        )

        val kaptLogger = MessageCollectorBackedKaptLogger(kaptOptions.build(), compilerMessageCollector)

        /** The main compiler plugin (MainComponentRegistrar)
         *  is instantiated by K2JVMCompiler using
         *  a service locator. So we can't just pass parameters to it easily.
         *  Instead we need to use a thread-local global variable to pass
         *  any parameters that change between compilations
         *
         */
        MainComponentRegistrar.threadLocalParameters.set(
            MainComponentRegistrar.ThreadLocalParameters(
                kaptModel.annotationProcessors.map {
                    IncrementalProcessor(
                        it,
                        DeclaredProcType.NON_INCREMENTAL,
                        kaptLogger
                    )
                },
                kaptOptions,
                model.compilerPlugins
            )
        )

        val kotlinSources = sourceFiles.filter(File::hasKotlinFileExtension)
        val javaSources = sourceFiles.filter(File::hasJavaFileExtension)

        val sourcePaths = mutableListOf<File>().apply {
            addAll(javaSources)

            if (kotlinSources.isNotEmpty()) {
                addAll(kotlinSources)
            } else {
                /* __HACK__: The K2JVMCompiler expects at least one Kotlin source file or it will crash.
                       We still need kapt to run even if there are no Kotlin sources because it executes APs
                       on Java sources as well. Alternatively we could call the JavaCompiler instead of kapt
                       to do annotation processing when there are only Java sources, but that's quite a lot
                       of work (It can not be done in the compileJava step because annotation processors on
                       Java files might generate Kotlin files which then need to be compiled in the
                       compileKotlin step before the compileJava step). So instead we trick K2JVMCompiler
                       by just including an empty .kt-File. */
                add(SourceFile.new("emptyKotlinFile.kt", "").writeIfNeeded(kaptModel.baseDir))
            }
        }.map(File::getAbsolutePath).distinct()

        if (!isJdk9OrLater()) {
            try {
                Class.forName("com.sun.tools.javac.util.Context")
            } catch (e: ClassNotFoundException) {
                val toolsJar = model.toolsJar
                require(toolsJar != null) {
                    "toolsJar must not be null on JDK 8 or earlier if it's classes aren't already on the classpath"
                }

                require(toolsJar.exists()) { "toolsJar file does not exist" }
                (ClassLoader.getSystemClassLoader() as URLClassLoader).addUrl(toolsJar.toURI().toURL())
            }
        }

        if (model.pluginClasspaths.isNotEmpty())
            model.warn("Included plugins in pluginsClasspaths will be executed twice.")

        val k2JvmArgs = compilation.commonK2JVMArgs().also {
            it.freeArgs = sourcePaths
            it.pluginClasspaths = (it.pluginClasspaths ?: emptyArray()) + arrayOf(compilation.getResourcesPath())
        }

        val exitCode = convertKotlinExitCode(
            K2JVMCompiler().exec(compilerMessageCollector, Services.EMPTY, k2JvmArgs)
        )
        return CompilationStep.IntermediateResult(
            exitCode = exitCode,
            generatedSources = kaptModel.kotlinGeneratedDir.listFilesRecursively() +
                    kaptModel.sourceDir.listFilesRecursively()
        )
    }
}

val KotlinCompilation.kapt: KaptModel
    get() = this.getOrPutExtensionData(KaptModel::class) {
            KaptModel(this).also {
                // TODO fix we shouldn't this cast
                addCompilationStep(KaptCompilationStep() as CompilationStep<AbstractKotlinCompilation<K2JVMCompilerArguments>>)
            }
        }
