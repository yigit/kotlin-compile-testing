package com.tschuchort.compiletesting

import com.facebook.buck.jvm.java.javax.com.tschuchort.compiletesting.JsCompilationModel
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import java.io.*

@Suppress("MemberVisibilityCanBePrivate")
class KotlinJsCompilation internal constructor(
  override val model: CompilationModelImpl.JsCompilationModelImpl
) : AbstractKotlinCompilation<K2JSCompilerArguments>(model), JsCompilationModel by model {
  constructor() : this(CompilationModelImpl.JsCompilationModelImpl())

  /** Result of the compilation */
  inner class Result(
    /** The exit code of the compilation */
    val exitCode: KotlinCompilation.ExitCode,
    /** Messages that were printed by the compilation */
    val messages: String
  ) {
    /** The directory where only the final output class and resources files will be */
    val outputDirectory: File get() = outputDir

    /**
     * Compiled class and resource files that are the final result of the compilation.
     */
    val compiledClassAndResourceFiles: List<File> = outputDirectory.listFilesRecursively()
  }


  // setup common arguments for the two kotlinc calls
  private fun jsArgs() = commonArguments(K2JSCompilerArguments()) { args ->
    // the compiler should never look for stdlib or reflect in the
    // kotlinHome directory (which is null anyway). We will put them
    // in the classpath manually if they're needed
    args.noStdlib = true

    args.moduleKind = "commonjs"
    args.outputFile = File(outputDir, outputFileName).absolutePath
    args.sourceMapBaseDirs = jsClasspath().joinToString(separator = File.pathSeparator)
    args.libraries = listOfNotNull(kotlinStdLibJsJar).joinToString(separator = ":")

    args.irProduceKlibDir = irProduceKlibDir
    args.irProduceKlibFile = irProduceKlibFile
    args.irProduceJs = irProduceJs
    args.irDce = irDce
    args.irDceDriven = irDceDriven
    args.irDcePrintReachabilityInfo = irDcePrintReachabilityInfo
    args.irOnly = irOnly
    args.irModuleName = irModuleName
  }

  /** Runs the compilation task */
  fun compile(): Result {
    // make sure all needed directories exist
    sourcesDir.mkdirs()
    outputDir.mkdirs()

    // write given sources to working directory
    val sourceFiles = sources.map { it.writeIfNeeded(sourcesDir) }

    pluginClasspaths.forEach { filepath ->
      if (!filepath.exists()) {
        error("Plugin $filepath not found")
        return makeResult(KotlinCompilation.ExitCode.INTERNAL_ERROR)
      }
    }


    /* Work around for warning that sometimes happens:
    "Failed to initialize native filesystem for Windows
    java.lang.RuntimeException: Could not find installation home path.
    Please make sure bin/idea.properties is present in the installation directory"
    See: https://github.com/arturbosch/detekt/issues/630
    */
    withSystemProperty("idea.use.native.fs.for.win", "false") {
      // step 1: compile Kotlin files
      return makeResult(compileKotlin(sourceFiles, K2JSCompiler(), jsArgs()))
    }
  }

  private fun makeResult(exitCode: KotlinCompilation.ExitCode): Result {
    val messages = model.internalMessageBuffer.readUtf8()

    if (exitCode != KotlinCompilation.ExitCode.OK)
      searchSystemOutForKnownErrors(messages)

    return Result(exitCode, messages)
  }

  private fun jsClasspath() = mutableListOf<File>().apply {
    addAll(classpaths)
    addAll(listOfNotNull(kotlinStdLibCommonJar, kotlinStdLibJsJar))

    if (inheritClassPath) {
      addAll(hostClasspaths)
      log("Inheriting classpaths:  " + hostClasspaths.joinToString(File.pathSeparator))
    }
  }.distinct()
}
