package com.tschuchort.compiletesting

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.convertKotlinExitCode
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import java.io.File

interface CompilationStep<A : CommonCompilerArguments> {
    fun execute(
        compilation: KotlinCompilation,
        sourceFiles: List<File>,
        buildCompilerArguments: () -> A,
        logger : (String) -> Unit
    ) : IntermediateResult

    data class IntermediateResult(
        val result: KotlinCompilation.ExitCode,
        val additionalSources: List<File>
    ) {
        companion object {
            val OK = IntermediateResult(
                result = KotlinCompilation.ExitCode.OK,
                additionalSources = emptyList()
            )
        }
    }
}

internal fun ExitCode.toIntermediateResult(
    additionalSources: List<File> = emptyList()
) = CompilationStep.IntermediateResult(
    result = convertKotlinExitCode(this),
    additionalSources = additionalSources
)
