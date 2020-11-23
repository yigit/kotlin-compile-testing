package com.tschuchort.compiletesting.step

import com.tschuchort.compiletesting.*
import com.tschuchort.compiletesting.param.JsCompilationModel
import org.jetbrains.kotlin.cli.js.K2JSCompiler

class JsCompilationStep : CompilationStep<JsCompilationModel> {
    override val id: String
        get() = ID

    override fun execute(
        env: CompilationEnvironment,
        compilationUtils: KotlinCompilationUtils,
        model: JsCompilationModel
    ): CompilationStep.IntermediateResult<JsCompilationModel> {
        // make sure all needed directories exist
        val sourcesDir = model.workingDir.resolve("sources").also {
            it.mkdirs()
        }
        model.outputDir.mkdirs()

        // write given sources to working directory
        val sourceFiles = model.sources.writeIfNeeded(sourcesDir)

        val exitCode = compilationUtils.compileKotlin(
            env = env,
            params = model,
            sources = sourceFiles,
            compiler = K2JSCompiler(),
            arguments = compilationUtils.prepareCommonJsArgs(model)
        )

        return CompilationStep.IntermediateResult(
            exitCode = exitCode,
            updatedModel = model,
            outputFolders = emptyList()
        )
    }

    companion object {
        val ID = "kotlinjs"
    }
}