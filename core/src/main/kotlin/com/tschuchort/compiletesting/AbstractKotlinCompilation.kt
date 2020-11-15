package com.tschuchort.compiletesting

import org.jetbrains.kotlin.base.kapt3.KaptOptions
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.arguments.validateArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.cli.jvm.plugins.ServiceLoaderLite
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.net.URI
import java.nio.file.Paths

/**
 * Base compilation class for sharing common compiler arguments and
 * functionality. Should not be used outside of this library as it is an
 * implementation detail.
 */
abstract class AbstractKotlinCompilation<A : CommonCompilerArguments> internal constructor(
    internal open val model: CompilationModelImpl
) : CompilationModel by model {
    private val _generatedSources = mutableListOf<File>()
    val generatedSources : List<File>
        get() = _generatedSources
    private val preCompilationSteps = mutableListOf<CompilationStep<AbstractKotlinCompilation<A>>>()
    private val postCompilationSteps = mutableListOf<CompilationStep<AbstractKotlinCompilation<A>>>()

    fun addPreCompilationStep(step : CompilationStep<AbstractKotlinCompilation<A>>) {
        if (preCompilationSteps.contains(step)) return
        preCompilationSteps.add(step)
    }
    fun addPostCompilationStep(step : CompilationStep<AbstractKotlinCompilation<A>>) {
        if (postCompilationSteps.contains(step)) return
        postCompilationSteps.add(step)
    }

    protected fun runPreCompilationSteps() = runSteps(preCompilationSteps)

    protected fun runPostCompilationSteps() = runSteps(postCompilationSteps)

    private fun runSteps(steps: List<CompilationStep<AbstractKotlinCompilation<A>>>): Pair<KotlinCompilation.ExitCode, List<File>> {
        // make sure all needed directories exist
        sourcesDir.mkdirs()
        // write given sources to working directory
        val sourceFiles = sources.map { it.writeIfNeeded(sourcesDir) }

        return steps.fold(
            KotlinCompilation.ExitCode.OK to sourceFiles
        ) { (exitCode, sources), step ->
            if (exitCode != KotlinCompilation.ExitCode.OK) {
                exitCode to sources
            }
            val intermediateResult = step.execute(
                compilation = this,
                sourceFiles = sourceFiles
            )
            _generatedSources.addAll(intermediateResult.generatedSources)
            intermediateResult.exitCode to sources + intermediateResult.generatedSources
        }
    }

    protected fun commonArguments(args: A, configuration: (args: A) -> Unit): A {
        args.pluginClasspaths = pluginClasspaths.map(File::getAbsolutePath).toTypedArray()

        args.verbose = verbose

        args.suppressWarnings = suppressWarnings
        args.allWarningsAsErrors = allWarningsAsErrors
        args.reportOutputFiles = reportOutputFiles
        args.reportPerf = reportPerformance

        configuration(args)

        /**
         * It's not possible to pass dynamic [CommandLineProcessor] instances directly to the [K2JSCompiler]
         * because the compiler discovers them on the classpath through a service locator, so we need to apply
         * the same trick as with [ComponentRegistrar]s: We put our own static [CommandLineProcessor] on the
         * classpath which in turn calls the user's dynamic [CommandLineProcessor] instances.
         */
        MainCommandLineProcessor.threadLocalParameters.set(
            MainCommandLineProcessor.ThreadLocalParameters(commandLineProcessors)
        )

        /**
         * Our [MainCommandLineProcessor] only has access to the CLI options that belong to its own plugin ID.
         * So in order to be able to access CLI options that are meant for other [CommandLineProcessor]s we
         * wrap these CLI options, send them to our own plugin ID and later unwrap them again to forward them
         * to the correct [CommandLineProcessor].
         */
        args.pluginOptions = pluginOptions.map { (pluginId, optionName, optionValue) ->
            "plugin:${MainCommandLineProcessor.pluginId}:${MainCommandLineProcessor.encodeForeignOptionName(pluginId, optionName)}=$optionValue"
        }.toTypedArray()

        /* Parse extra CLI arguments that are given as strings so users can specify arguments that are not yet
        implemented here as well-typed properties. */
        parseCommandLineArguments(kotlincArguments, args)

        validateArguments(args.errors)?.let {
            throw IllegalArgumentException("Errors parsing kotlinc CLI arguments:\n$it")
        }

        return args
    }

    /** Performs the compilation step to compile Kotlin source files */
    protected fun compileKotlin(sources: List<File>, compiler: CLICompiler<A>, arguments: A): KotlinCompilation.ExitCode {

        /**
         * Here the list of compiler plugins is set
         *
         * To avoid that the annotation processors are executed twice,
         * the list is set to empty
         */
        MainComponentRegistrar.threadLocalParameters.set(
            MainComponentRegistrar.ThreadLocalParameters(
                listOf(),
                KaptOptions.Builder(),
                compilerPlugins
            )
        )

        // if no Kotlin sources are available, skip the compileKotlin step
        if (sources.none(File::hasKotlinFileExtension))
            return KotlinCompilation.ExitCode.OK

        // in this step also include source files generated by kapt in the previous step
        val args = arguments.also { args ->
            args.freeArgs = sources.map(File::getAbsolutePath).distinct()
            args.pluginClasspaths = (args.pluginClasspaths ?: emptyArray()) + arrayOf(getResourcesPath())
        }

        val compilerMessageCollector = PrintingMessageCollector(
            model.internalMessageStream, MessageRenderer.GRADLE_STYLE, verbose
        )

        return convertKotlinExitCode(
            compiler.exec(compilerMessageCollector, Services.EMPTY, args)
        )
    }

    internal fun getResourcesPath(): String {
        val resourceName = "META-INF/services/org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar"
        return this::class.java.classLoader.getResources(resourceName)
            .asSequence()
            .mapNotNull { url ->
                val uri = URI.create(url.toString().removeSuffix("/$resourceName"))
                when (uri.scheme) {
                    "jar" -> Paths.get(URI.create(uri.schemeSpecificPart.removeSuffix("!")))
                    "file" -> Paths.get(uri)
                    else -> return@mapNotNull null
                }.toAbsolutePath()
            }
            .find { resourcesPath ->
                ServiceLoaderLite.findImplementations(ComponentRegistrar::class.java, listOf(resourcesPath.toFile()))
                    .any { implementation -> implementation == MainComponentRegistrar::class.java.name }
            }?.toString() ?: throw AssertionError("Could not get path to ComponentRegistrar service from META-INF")
    }

    /** Searches compiler log for known errors that are hard to debug for the user */
    protected fun searchSystemOutForKnownErrors(compilerSystemOut: String) {
        if (compilerSystemOut.contains("No enum constant com.sun.tools.javac.main.Option.BOOT_CLASS_PATH")) {
            model.warn(
                "${this::class.simpleName} has detected that the compiler output contains an error message that may be " +
                        "caused by including a tools.jar file together with a JDK of version 9 or later. " +
                        if (inheritClassPath)
                            "Make sure that no tools.jar (or unwanted JDK) is in the inherited classpath"
                        else ""
            )
        }

        if (compilerSystemOut.contains("Unable to find package java.")) {
            model.warn(
                "${this::class.simpleName} has detected that the compiler output contains an error message " +
                        "that may be caused by a missing JDK. This can happen if jdkHome=null and inheritClassPath=false."
            )
        }
    }
}

internal fun kotlinDependencyRegex(prefix:String): Regex {
    return Regex("$prefix(-[0-9]+\\.[0-9]+(\\.[0-9]+)?)([-0-9a-zA-Z]+)?\\.jar")
}

internal fun convertKotlinExitCode(code: ExitCode) = when(code) {
    ExitCode.OK -> KotlinCompilation.ExitCode.OK
    ExitCode.INTERNAL_ERROR -> KotlinCompilation.ExitCode.INTERNAL_ERROR
    ExitCode.COMPILATION_ERROR -> KotlinCompilation.ExitCode.COMPILATION_ERROR
    ExitCode.SCRIPT_EXECUTION_ERROR -> KotlinCompilation.ExitCode.SCRIPT_EXECUTION_ERROR
}
