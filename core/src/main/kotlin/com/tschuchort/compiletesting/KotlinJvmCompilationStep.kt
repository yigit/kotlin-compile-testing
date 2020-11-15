package com.tschuchort.compiletesting

import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.File

class KotlinJvmCompilationStep : CompilationStep<KotlinCompilation> {
    override val order: CompilationStep.Order
        get() = CompilationStep.Order.KOTLIN_COMPILATION

    override fun execute(compilation: KotlinCompilation, sourceFiles: List<File>): CompilationStep.IntermediateResult {
        val exitCode = compilation.compileKotlin(sourceFiles, K2JVMCompiler(), compilation.commonK2JVMArgs())
        return CompilationStep.IntermediateResult(
            exitCode = exitCode,
            generatedSources = emptyList()
        )
    }
}