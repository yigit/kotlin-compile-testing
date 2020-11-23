package com.tschuchort.compiletesting.step

import com.facebook.buck.jvm.java.javax.com.tschuchort.compiletesting.step.KotlinJvmCompilationStep
import com.tschuchort.compiletesting.*
import com.tschuchort.compiletesting.param.JvmCompilationModel
import org.jetbrains.kotlin.base.kapt3.AptMode
import org.jetbrains.kotlin.base.kapt3.KaptFlag
import org.jetbrains.kotlin.base.kapt3.KaptOptions
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.kapt3.base.incremental.DeclaredProcType
import org.jetbrains.kotlin.kapt3.base.incremental.IncrementalProcessor
import org.jetbrains.kotlin.kapt3.util.MessageCollectorBackedKaptLogger
import java.io.File
import java.net.URLClassLoader
import javax.annotation.processing.Processor


class KaptParameters(
    private val compilationParams: JvmCompilationModel
) {
    /** Arbitrary arguments to be passed to kapt */
    var args = mutableMapOf<OptionName, OptionValue>()
    /** Make kapt correct error types */
    var correctErrorTypes: Boolean = false
    /** Annotation processors to be passed to kapt */
    var annotationProcessors: List<Processor> = emptyList()
    // Base directory for kapt stuff
    val baseDir get() = compilationParams.workingDir.resolve("kapt")

    // Java annotation processors that are compile by kapt will put their generated files here
    val sourceDir get() = baseDir.resolve("outputSource")

    // Output directory for Kotlin source files generated by kapt
    val kotlinGeneratedDir get() = args[KotlinCompilation.OPTION_KAPT_KOTLIN_GENERATED]
        ?.let { path ->
            require(File(path).isDirectory) { "${KotlinCompilation.OPTION_KAPT_KOTLIN_GENERATED} must be a directory" }
            File(path)
        }
        ?: File(baseDir, "kotlinGenerated")

    val stubsDir get() = baseDir.resolve("stubs")
    val incrementalDataDir get() = baseDir.resolve("incrementalData")

    val classesDir get() = baseDir.resolve("classes")

    internal val inputSourceDir get() = baseDir.resolve("inputSource")

    internal fun mkdirs() {
        listOf(baseDir, sourceDir, kotlinGeneratedDir,
        stubsDir, incrementalDataDir, classesDir, inputSourceDir).forEach {
            it.deleteRecursively()
            it.mkdirs()
        }
    }
}

class KaptCompilationStep : CompilationStep<JvmCompilationModel> {
    override val id: String
        get() = ID
    override fun execute(
        env: CompilationEnvironment,
        compilationUtils: KotlinCompilationUtils,
        model: JvmCompilationModel
    ): CompilationStep.IntermediateResult<JvmCompilationModel> {
        val kaptParams = model.kapt ?: return CompilationStep.IntermediateResult.skip(model)
        kaptParams.mkdirs()
        val exitCode = stubsAndApt(
            env = env,
            compilationUtils = compilationUtils, // move this to the env?
            params = model,
            kaptParams = kaptParams
        )
        val generatedSourceFiles = kaptParams.sourceDir.listFilesRecursively().map {
            SourceFile.fromPath(it)
        } + kaptParams.kotlinGeneratedDir.listFilesRecursively().map {
            SourceFile.fromPath(it)
        }
        return CompilationStep.IntermediateResult(
            exitCode = exitCode,
            updatedModel = OutputCompilationModel(
                delegate = model,
                generatedSources = generatedSourceFiles
            ),
            outputFolders = listOf(kaptParams.classesDir, kaptParams.kotlinGeneratedDir, kaptParams.stubsDir,
            kaptParams.sourceDir)
        )
    }

    /** Performs the 1st and 2nd compilation step to generate stubs and run annotation processors */
    private fun stubsAndApt(
        env: CompilationEnvironment,
        compilationUtils: KotlinCompilationUtils,
        params: JvmCompilationModel,
        kaptParams: KaptParameters
    ): KotlinCompilation.ExitCode {

        if(kaptParams.annotationProcessors.isEmpty()) {
            env.messageStream.log("No services were given. Not running kapt steps.")
            return KotlinCompilation.ExitCode.OK
        }
        val kaptOptions = KaptOptions.Builder().also {
            it.stubsOutputDir = kaptParams.stubsDir
            it.sourcesOutputDir = kaptParams.sourceDir
            it.incrementalDataOutputDir = kaptParams.incrementalDataDir
            it.classesOutputDir = kaptParams.classesDir
            it.processingOptions.apply {
                putAll(kaptParams.args)
                putIfAbsent(KotlinCompilation.OPTION_KAPT_KOTLIN_GENERATED, kaptParams.kotlinGeneratedDir.absolutePath)
            }

            it.mode = AptMode.STUBS_AND_APT

            if (params.verbose)
                it.flags.addAll(KaptFlag.MAP_DIAGNOSTIC_LOCATIONS, KaptFlag.VERBOSE)
        }

        val compilerMessageCollector = env.messageStream.createMessageCollector()

        val kaptLogger = MessageCollectorBackedKaptLogger(kaptOptions.build(), compilerMessageCollector)

        /** The main compiler plugin (MainComponentRegistrar)
         *  is instantiated by K2JVMCompiler using
         *  a service locator. So we can't just pass parameters to it easily.
         *  Instead we need to use a thread-local global variable to pass
         *  any parameters that change between compilations
         *
         */
        // TODO move this to an API
        MainComponentRegistrar.threadLocalParameters.set(
            MainComponentRegistrar.ThreadLocalParameters(
                kaptParams.annotationProcessors.map { IncrementalProcessor(it, DeclaredProcType.NON_INCREMENTAL, kaptLogger) },
                kaptOptions,
                params.compilerPlugins
            )
        )

        val sourceFiles = params.sources.map { it.writeIfNeeded(kaptParams.inputSourceDir) }
        val kotlinSources = sourceFiles.filter(File::hasKotlinFileExtension)
        val javaSources = sourceFiles.filter(File::hasJavaFileExtension)

        val sourcePaths = mutableListOf<File>().apply {
            addAll(javaSources)

            if(kotlinSources.isNotEmpty()) {
                addAll(kotlinSources)
            }
            else {
                /* __HACK__: The K2JVMCompiler expects at least one Kotlin source file or it will crash.
                   We still need kapt to run even if there are no Kotlin sources because it executes APs
                   on Java sources as well. Alternatively we could call the JavaCompiler instead of kapt
                   to do annotation processing when there are only Java sources, but that's quite a lot
                   of work (It can not be done in the compileJava step because annotation processors on
                   Java files might generate Kotlin files which then need to be compiled in the
                   compileKotlin step before the compileJava step). So instead we trick K2JVMCompiler
                   by just including an empty .kt-File. */
                add(SourceFile.new("emptyKotlinFile.kt", "").writeIfNeeded(kaptParams.sourceDir))
            }
        }.map(File::getAbsolutePath).distinct()

        if(!isJdk9OrLater()) {
            try {
                Class.forName("com.sun.tools.javac.util.Context")
            }
            catch (e: ClassNotFoundException) {
                require(params.toolsJar != null) {
                    "toolsJar must not be null on JDK 8 or earlier if it's classes aren't already on the classpath"
                }

                require(params.toolsJar!!.exists()) { "toolsJar file does not exist" }
                (ClassLoader.getSystemClassLoader() as URLClassLoader).addUrl(params.toolsJar!!.toURI().toURL())
            }
        }

        if (params.pluginClasspaths.isNotEmpty())
            env.messageStream.warn("Included plugins in pluginsClasspaths will be executed twice.")

        val k2JvmArgs = compilationUtils.prepareCommonK2JVMArgs(
            params = params,
            outputParams = KotlinCompilationUtils.OutputParameters(kaptParams.classesDir)
        ).also {
            it.freeArgs = sourcePaths
            it.pluginClasspaths = (it.pluginClasspaths ?: emptyArray()) + arrayOf(env.getResourcesPath())
        }
        return convertKotlinExitCode(
            K2JVMCompiler().exec(compilerMessageCollector, Services.EMPTY, k2JvmArgs)
        )
    }

    private class OutputCompilationModel(
        delegate: JvmCompilationModel,
        generatedSources : List<SourceFile>
    ) : JvmCompilationModel by delegate {
        private val combinedSources = delegate.sources + generatedSources
        override val sources: List<SourceFile>
            get() = combinedSources
    }

    companion object {
        val ID = "KAPT"
    }
}

private val JvmCompilationModel.kapt: KaptParameters?
    get() = this.getExtensionData(KaptParameters::class)

val KotlinCompilation.kapt: KaptParameters
    get() = this.model().getOrPutExtensionData(KaptParameters::class) {
        KaptParameters(model()).also {
            this@kapt.registerStep(
                step = KaptCompilationStep(),
                shouldRunBefore = setOf(
                    KotlinJvmCompilationStep.ID
                ))
        }
    }