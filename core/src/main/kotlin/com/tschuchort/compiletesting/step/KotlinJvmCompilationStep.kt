package com.tschuchort.compiletesting.step

import com.tschuchort.compiletesting.*
import com.tschuchort.compiletesting.param.JvmCompilationModel
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.File

class KotlinJvmCompilationStep : CompilationStep<JvmCompilationModel> {
    override val id: String
        get() = ID

    override fun execute(
        env: HostEnvironment,
        compilationUtils: KotlinCompilationUtils,
        model: JvmCompilationModel
    ): CompilationStep.IntermediateResult<JvmCompilationModel> {
        val baseDir = model.workingDir.resolve("jvmCompilation")
        baseDir.mkdirs()
        val inputSourcesDir = baseDir.resolve("src").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        val sourceFiles = model.sources.writeIfNeeded(inputSourcesDir)
        val classesOutDir = baseDir.resolve("classes").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        val args = compilationUtils.prepareCommonK2JVMArgs(
            params = model,
            outputParams = KotlinCompilationUtils.JvmOutputParams(
                classesDir = classesOutDir
            )
        )
        val result = compilationUtils.compileKotlin(
            env = env,
            params = model,
            sources = sourceFiles,
            compiler = K2JVMCompiler(),
            arguments = args
        )
        return CompilationStep.IntermediateResult(
            exitCode = result,
            updatedModel = OutputCompilationModel(
                delegate = model,
                kotlinCompilationClassesDir = classesOutDir
            ),
            resultPayload = CompilationStep.ResultPayload(
                generatedSourceDirs = emptyList(),
                outputDirs = listOf(classesOutDir)
            )
        )
    }

    private class OutputCompilationModel(
        val delegate: JvmCompilationModel,
        val kotlinCompilationClassesDir: File
    ) : JvmCompilationModel by delegate {
        override val classpaths: List<File>
            get() = delegate.classpaths + listOf(kotlinCompilationClassesDir)
    }

    companion object {
        val ID = "kotlin-jvm-compilation"
    }
}