package com.tschuchort.compiletesting.step

import com.tschuchort.compiletesting.HostEnvironment
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
        env: HostEnvironment,
        compilationUtils: KotlinCompilationUtils,
        model: Model
    ): IntermediateResult<Model>

    class IntermediateResult<Params: CompilationModel>(
        val exitCode: KotlinCompilation.ExitCode,
        val updatedModel: Params,
        // payload that can be assigned to a result
        val resultPayload: ResultPayload
    ) {
        companion object {
            fun <Params : CompilationModel> skip(params: Params)  = IntermediateResult(
                exitCode = KotlinCompilation.ExitCode.OK,
                updatedModel = params,
                resultPayload = ResultPayload.EMPTY
            )
        }
    }

    class ResultPayload(
        val generatedSourceDirs: List<File>,
        val outputDirs: List<File>
    ) {
        companion object {
            val EMPTY = ResultPayload(
                generatedSourceDirs = emptyList(),
                outputDirs = emptyList()
            )
        }
    }
}