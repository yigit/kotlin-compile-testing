package com.tschuchort.compiletesting

import com.tschuchort.compiletesting.CompilationStep
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import java.io.File

class KotlinJSCompilationStep : CompilationStep<KotlinJsCompilation> {
    override val order: CompilationStep.Order
        get() = CompilationStep.Order.KOTLIN_COMPILATION

    override fun execute(
        compilation: KotlinJsCompilation,
        sourceFiles: List<File>
    ): CompilationStep.IntermediateResult {
        /* Work around for warning that sometimes happens:
    "Failed to initialize native filesystem for Windows
    java.lang.RuntimeException: Could not find installation home path.
    Please make sure bin/idea.properties is present in the installation directory"
    See: https://github.com/arturbosch/detekt/issues/630
    */
        val exitCode = withSystemProperty("idea.use.native.fs.for.win", "false") {
            compilation.compileKotlin(sourceFiles, K2JSCompiler(), compilation.jsArgs())
        }
        return CompilationStep.IntermediateResult(
            exitCode = exitCode,
            generatedSources = emptyList()
        )
    }
}