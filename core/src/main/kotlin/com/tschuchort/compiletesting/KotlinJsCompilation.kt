package com.tschuchort.compiletesting

import com.tschuchort.compiletesting.param.JsCompilationModel
import com.tschuchort.compiletesting.param.JsCompilationModelImpl
import com.tschuchort.compiletesting.step.JsCompilationStep
import java.io.*

@Suppress("MemberVisibilityCanBePrivate")
class KotlinJsCompilation internal constructor(
   private val model: JsCompilationModelImpl
): AbstractKotlinCompilation<JsCompilationModel>(model) {
  constructor(): this(JsCompilationModelImpl())

  var outputFileName: String by model::outputFileName

  /**
   * Generate unpacked KLIB into parent directory of output JS file. In combination with -meta-info
   * generates both IR and pre-IR versions of library.
   */
  var irProduceKlibDir: Boolean by model::irProduceKlibDir

  /** Generate packed klib into file specified by -output. Disables pre-IR backend */
  var irProduceKlibFile: Boolean by model::irProduceKlibFile

  /** Generates JS file using IR backend. Also disables pre-IR backend */
  var irProduceJs: Boolean by model::irProduceJs

  /** Perform experimental dead code elimination */
  var irDce: Boolean by model::irDce

  /** Perform a more experimental faster dead code elimination */
  var irDceDriven: Boolean by model::irDceDriven

  /** Print declarations' reachability info to stdout during performing DCE */
  var irDcePrintReachabilityInfo: Boolean by model::irDcePrintReachabilityInfo

  /** Disables pre-IR backend */
  var irOnly: Boolean by model::irOnly

  /** Specify a compilation module name for IR backend */
  var irModuleName: String? by model::irModuleName

  /**
   * Path to the kotlin-stdlib-js.jar
   * If none is given, it will be searched for in the host
   * process' classpaths
   */
  var kotlinStdLibJsJar: File? by model::kotlinStdLibJsJar

  // *.class files, Jars and resources (non-temporary) that are created by the
  // compilation will land here
  val outputDir by model::outputDir

  /** Result of the compilation */
  class Result(
    /** The exit code of the compilation */
    val exitCode: KotlinCompilation.ExitCode,
    /** Messages that were printed by the compilation */
    val messages: String,
    /** The directory where only the final output class and resources files will be */
    val outputDirectory: File
  ) {
    /**
     * Compiled class and resource files that are the final result of the compilation.
     */
    val compiledClassAndResourceFiles: List<File> = outputDirectory.listFilesRecursively()
  }




  /** Runs the compilation task */
  fun compile(): Result {
    model.pluginClasspaths.forEach { filepath ->
      if (!filepath.exists()) {
        model.messageStream.error("Plugin $filepath not found")
        return makeResult(KotlinCompilation.ExitCode.INTERNAL_ERROR)
      }
    }
    if (!hasStep(JsCompilationStep.ID)) {
      registerStep(
        step = JsCompilationStep()
      )
    }

    val intermediateResult = runSteps()
    return makeResult(
      exitCode = intermediateResult.exitCode
    )
  }

  private fun makeResult(exitCode: KotlinCompilation.ExitCode): Result {
    val messages = model.messageStream.collectLog()

    if (exitCode != KotlinCompilation.ExitCode.OK)
      searchSystemOutForKnownErrors(messages)

    return Result(exitCode, messages, model.outputDir)
  }
}
