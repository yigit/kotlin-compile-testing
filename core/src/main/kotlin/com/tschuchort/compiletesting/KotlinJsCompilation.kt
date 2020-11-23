package com.tschuchort.compiletesting

import com.tschuchort.compiletesting.param.JsCompilationModel
import com.tschuchort.compiletesting.param.JsCompilationModelImpl
import com.tschuchort.compiletesting.step.JsCompilationStep
import com.tschuchort.compiletesting.step.StepRegistry
import java.io.*
import java.util.*

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

  /** Result of the compilation */
  class Result(
    /** The exit code of the compilation */
    val exitCode: KotlinCompilation.ExitCode,
    /** Messages that were printed by the compilation */
    val messages: String,
    /** The directory where only the final output class and resources files will be */
    val outputDirectory: File,
    val extraData: HasExtensionData
  ) : HasExtensionData by extraData {
    /**
     * Compiled class and resource files that are the final result of the compilation.
     */
    val compiledClassAndResourceFiles: List<File> = outputDirectory.listFilesRecursively()
  }




  /** Runs the compilation task */
  fun compile(): Result {
    val compilationModel = SingleCompilationModel(model)
    model.pluginClasspaths.forEach { filepath ->
      if (!filepath.exists()) {
        model.messageStream.error("Plugin $filepath not found")
        return makeInternalErrorResult(compilationModel)
      }
    }
    if (!hasStep(JsCompilationStep.ID)) {
      registerStep(
        step = JsCompilationStep()
      )
    }


    val intermediateResult = runSteps(compilationModel)
    return makeResult(
      compilationModel,
      intermediateResult
    )
  }

  private fun makeInternalErrorResult(
    compilationModel: SingleCompilationModel
  ): Result {
    val messages = model.messageStream.collectLog()

    searchSystemOutForKnownErrors(messages)
    val errorOutputDir = compilationModel.workingDir.resolve("final-output").also {
      it.deleteRecursively()
      it.mkdirs()
    }
    // figure out how to collect output directories
    return Result(
      exitCode = KotlinCompilation.ExitCode.INTERNAL_ERROR,
      messages = messages,
      outputDirectory = errorOutputDir,
      extraData = HasExtensionDataImpl()
    )
  }

  private fun makeResult(
    compilationModel: SingleCompilationModel,
    executionResult: StepRegistry.ExecutionResult
  ): Result {
    val messages = model.messageStream.collectLog()

    if (executionResult.exitCode != KotlinCompilation.ExitCode.OK)
      searchSystemOutForKnownErrors(messages)
    val outputDirectory = compilationModel.workingDir.resolve("final-output").also {
      it.deleteRecursively()
      it.mkdirs()
    }

    executionResult.resultPayloads.forEach {
      it.outputDirs.forEach {
        it.copyRecursively(outputDirectory)
      }
    }
    return Result(
      exitCode = executionResult.exitCode,
      messages = messages,
      outputDirectory = outputDirectory,
      extraData = executionResult)
  }

  private class SingleCompilationModel(
    val delegate: JsCompilationModel
  ) : JsCompilationModel by delegate {
    private val individualWorkingDir = delegate.workingDir.resolve(UUID.randomUUID().toString().take(6))
    override val workingDir: File
      get() = individualWorkingDir
  }
}
