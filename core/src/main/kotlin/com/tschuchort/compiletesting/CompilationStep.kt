package com.tschuchort.compiletesting

import java.io.File

interface CompilationStep<in Compilation : AbstractKotlinCompilation<*>> {
    val order:Order
    fun execute(
        compilation: Compilation,
        sourceFiles: List<File>
    ) : IntermediateResult

    enum class Order {
        PRE_KOTLIN_COMPILATION,
        KOTLIN_COMPILATION,
        POST_KOTLIN_COMPILATION
    }

    data class IntermediateResult(
        val exitCode: KotlinCompilation.ExitCode,
        val generatedSources: List<File>
    ) {

        companion object {
            val SKIP = IntermediateResult(
                exitCode = KotlinCompilation.ExitCode.OK,
                generatedSources = emptyList()
            )
        }
    }
}
