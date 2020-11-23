package com.tschuchort.compiletesting.step

import com.tschuchort.compiletesting.*
import com.tschuchort.compiletesting.param.JsCompilationModel
import org.jetbrains.kotlin.cli.js.K2JSCompiler

class JsCompilationStep : CompilationStep<JsCompilationModel> {
    override val id: String
        get() = ID

    override fun execute(
        env: HostEnvironment,
        compilationUtils: KotlinCompilationUtils,
        model: JsCompilationModel
    ): CompilationStep.IntermediateResult<JsCompilationModel> {
        // make sure all needed directories exist
        val sourcesDir = model.workingDir.resolve("sources").also {
            it.mkdirs()
        }
        val outputDir = model.workingDir.resolve("js-compilation-output").also {
            it.deleteRecursively()
            it.mkdirs()
        }

        // write given sources to working directory
        val sourceFiles = model.sources.writeIfNeeded(sourcesDir)

        val exitCode = compilationUtils.compileKotlin(
            env = env,
            params = model,
            sources = sourceFiles,
            compiler = K2JSCompiler(),
            arguments = compilationUtils.prepareCommonJsArgs(model, KotlinCompilationUtils.JsOutputParams(
                outFile = outputDir.resolve(model.outputFileName)
            ))
        )

        return CompilationStep.IntermediateResult(
            exitCode = exitCode,
            updatedModel = model,
            resultPayload = CompilationStep.ResultPayload(
                generatedSourceDirs = emptyList(),
                outputDirs = listOf(outputDir)
            )
        )
    }

    companion object {
        val ID = "kotlinjs"
    }
}