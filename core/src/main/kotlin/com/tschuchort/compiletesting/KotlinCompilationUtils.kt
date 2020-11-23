package com.tschuchort.compiletesting

import com.tschuchort.compiletesting.param.CompilationModel
import com.tschuchort.compiletesting.param.JsCompilationModel
import com.tschuchort.compiletesting.param.JvmCompilationModel
import org.jetbrains.kotlin.base.kapt3.KaptOptions
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.Services
import java.io.File

class KotlinCompilationUtils(
    val messageStream: MessageStream,
    val env: CompilationEnvironment
) {
    fun <A : CommonCompilerArguments> prepareCommonArguments(
        params: CompilationModel,
        args: A,
        configuration: (args: A) -> Unit): A {
        args.pluginClasspaths = params.pluginClasspaths.map(File::getAbsolutePath).toTypedArray()

        args.verbose = params.verbose

        args.suppressWarnings = params.suppressWarnings
        args.allWarningsAsErrors = params.allWarningsAsErrors
        args.reportOutputFiles = params.reportOutputFiles
        args.reportPerf = params.reportPerformance

        configuration(args)

        /**
         * It's not possible to pass dynamic [CommandLineProcessor] instances directly to the [K2JSCompiler]
         * because the compiler discovers them on the classpath through a service locator, so we need to apply
         * the same trick as with [ComponentRegistrar]s: We put our own static [CommandLineProcessor] on the
         * classpath which in turn calls the user's dynamic [CommandLineProcessor] instances.
         */
        MainCommandLineProcessor.threadLocalParameters.set(
            MainCommandLineProcessor.ThreadLocalParameters(params.commandLineProcessors)
        )

        /**
         * Our [MainCommandLineProcessor] only has access to the CLI options that belong to its own plugin ID.
         * So in order to be able to access CLI options that are meant for other [CommandLineProcessor]s we
         * wrap these CLI options, send them to our own plugin ID and later unwrap them again to forward them
         * to the correct [CommandLineProcessor].
         */
        args.pluginOptions = params.pluginOptions.map { (pluginId, optionName, optionValue) ->
            "plugin:${MainCommandLineProcessor.pluginId}:${MainCommandLineProcessor.encodeForeignOptionName(pluginId, optionName)}=$optionValue"
        }.toTypedArray()

        /* Parse extra CLI arguments that are given as strings so users can specify arguments that are not yet
        implemented here as well-typed properties. */
        parseCommandLineArguments(params.kotlincArguments, args)

        validateArguments(args.errors)?.let {
            throw IllegalArgumentException("Errors parsing kotlinc CLI arguments:\n$it")
        }

        return args
    }
    // setup common arguments for the kotlinc calls
    // setup common arguments for the two kotlinc calls
    fun prepareCommonK2JVMArgs(
        params: JvmCompilationModel,
        outputParams: OutputParameters
    ) = prepareCommonArguments(params, K2JVMCompilerArguments()) { args ->
        args.destination = outputParams.classesDir.absolutePath
        args.classpath = commonClasspaths(params).joinToString(separator = File.pathSeparator)

        if(params.jdkHome != null) {
            args.jdkHome = params.jdkHome!!.absolutePath
        }
        else {
            messageStream.log("Using option -no-jdk. Kotlinc won't look for a JDK.")
            args.noJdk = true
        }

        args.includeRuntime = params.includeRuntime

        // the compiler should never look for stdlib or reflect in the
        // kotlinHome directory (which is null anyway). We will put them
        // in the classpath manually if they're needed
        args.noStdlib = true
        args.noReflect = true

        if(params.moduleName != null)
            args.moduleName = params.moduleName

        args.jvmTarget = params.jvmTarget
        args.javaParameters = params.javaParameters
        args.useIR = params.useIR

        if(params.javaModulePath != null)
            args.javaModulePath = params.javaModulePath!!.toString()

        args.additionalJavaModules = params.additionalJavaModules.map(File::getAbsolutePath).toTypedArray()
        args.noCallAssertions = params.noCallAssertions
        args.noParamAssertions = params.noParamAssertions
        args.noReceiverAssertions = params.noReceiverAssertions
        args.strictJavaNullabilityAssertions = params.strictJavaNullabilityAssertions
        args.noOptimize = params.noOptimize

        if(params.constructorCallNormalizationMode != null)
            args.constructorCallNormalizationMode = params.constructorCallNormalizationMode

        if(params.assertionsMode != null)
            args.assertionsMode = params.assertionsMode

        if(params.buildFile != null)
            args.buildFile = params.buildFile!!.toString()

        args.inheritMultifileParts = params.inheritMultifileParts
        args.useTypeTable = params.useTypeTable

        if(params.declarationsOutputPath != null)
            args.declarationsOutputPath = params.declarationsOutputPath!!.toString()

        args.singleModule = params.singleModule

        if(params.javacArguments.isNotEmpty())
            args.javacArguments = params.javacArguments.toTypedArray()

        if(params.supportCompatqualCheckerFrameworkAnnotations != null)
            args.supportCompatqualCheckerFrameworkAnnotations = params.supportCompatqualCheckerFrameworkAnnotations

        args.jvmDefault = params.jvmDefault
        args.strictMetadataVersionSemantics = params.strictMetadataVersionSemantics
        args.sanitizeParentheses = params.sanitizeParentheses

        if(params.friendPaths.isNotEmpty())
            args.friendPaths = params.friendPaths.map(File::getAbsolutePath).toTypedArray()

        if(params.scriptResolverEnvironment.isNotEmpty())
            args.scriptResolverEnvironment = params.scriptResolverEnvironment.map { (key, value) -> "$key=\"$value\"" }.toTypedArray()

        args.noExceptionOnExplicitEqualsForBoxedNull = params.noExceptionOnExplicitEqualsForBoxedNull
        args.skipRuntimeVersionCheck = params.skipRuntimeVersionCheck
        args.javaPackagePrefix = params.javaPackagePrefix
        args.suppressMissingBuiltinsError = params.suppressMissingBuiltinsError
    }

    fun commonClasspaths(params: JvmCompilationModel) = mutableListOf<File>().apply {
        addAll(params.classpaths)
        addAll(listOfNotNull(params.kotlinStdLibJar, params.kotlinStdLibCommonJar, params.kotlinStdLibJdkJar,
            params.kotlinReflectJar, params.kotlinScriptRuntimeJar
        ))

        if(params.inheritClassPath) {
            addAll(env.hostClasspaths)
            messageStream.log("Inheriting classpaths:  " + env.hostClasspaths.joinToString(File.pathSeparator))
        }
    }.distinct()

    /** Performs the compilation step to compile Kotlin source files */
    fun <Args : CommonCompilerArguments> compileKotlin(
        env:CompilationEnvironment,
        params: CompilationModel,
        sources: List<File>,
        compiler: CLICompiler<Args>,
        arguments: Args
    ): KotlinCompilation.ExitCode {

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
                params.compilerPlugins
            )
        )

        // if no Kotlin sources are available, skip the compileKotlin step
        if (sources.none(File::hasKotlinFileExtension))
            return KotlinCompilation.ExitCode.OK

        // in this step also include source files generated by kapt in the previous step
        val args = arguments.also { args ->
            args.freeArgs = sources.map(File::getAbsolutePath).distinct()
            args.pluginClasspaths = (args.pluginClasspaths ?: emptyArray()) + arrayOf(env.getResourcesPath())
        }

        val compilerMessageCollector = env.messageStream.createMessageCollector()

        return convertKotlinExitCode(
            compiler.exec(compilerMessageCollector, Services.EMPTY, args)
        )
    }

    // setup common arguments for the two kotlinc calls
    fun prepareCommonJsArgs(
        model: JsCompilationModel
    ) = prepareCommonArguments(
        params = model,
        args = K2JSCompilerArguments()) { args ->
        // the compiler should never look for stdlib or reflect in the
        // kotlinHome directory (which is null anyway). We will put them
        // in the classpath manually if they're needed
        args.noStdlib = true

        args.moduleKind = "commonjs"
        args.outputFile = File(model.outputDir, model.outputFileName).absolutePath
        args.sourceMapBaseDirs = jsClasspath(model).joinToString(separator = File.pathSeparator)
        args.libraries = listOfNotNull(model.kotlinStdLibJsJar).joinToString(separator = ":")

        args.irProduceKlibDir = model.irProduceKlibDir
        args.irProduceKlibFile = model.irProduceKlibFile
        args.irProduceJs = model.irProduceJs
        args.irDce = model.irDce
        args.irDceDriven = model.irDceDriven
        args.irDcePrintReachabilityInfo = model.irDcePrintReachabilityInfo
        args.irOnly = model.irOnly
        args.irModuleName = model.irModuleName
    }

    fun jsClasspath(model: JsCompilationModel) = mutableListOf<File>().apply {
        addAll(model.classpaths)
        addAll(listOfNotNull(model.kotlinStdLibCommonJar, model.kotlinStdLibJsJar))

        if (model.inheritClassPath) {
            addAll(env.hostClasspaths)
            env.messageStream.log("Inheriting classpaths:  " + env.hostClasspaths.joinToString(File.pathSeparator))
        }
    }.distinct()

    class OutputParameters(
        val classesDir: File
    )
}