package com.tschuchort.compiletesting.step

import com.tschuchort.compiletesting.CompilationEnvironment
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilationUtils
import com.tschuchort.compiletesting.param.CompilationModel
import java.io.File

interface CompilationStep<Model: CompilationModel> {
    /**
     * unique id to resolve dependencies
     */
    val id: String
    fun execute(
        env: CompilationEnvironment,
        compilationUtils: KotlinCompilationUtils,
        model: Model
    ): IntermediateResult<Model>

    class IntermediateResult<Params: CompilationModel>(
        val exitCode: KotlinCompilation.ExitCode,
        val updatedModel: Params,
        // outputs for Result, must be a folder
        val outputFolders: List<File>
    ) {
        init {
            require(outputFolders.all { it.isDirectory }) {
                "each output folder must be a directory"
            }
        }
        companion object {
            fun <Params : CompilationModel> skip(params: Params)  = IntermediateResult(
                exitCode = KotlinCompilation.ExitCode.OK,
                updatedModel = params,
                outputFolders = emptyList()
            )
        }
    }
}