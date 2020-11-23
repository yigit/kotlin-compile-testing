package com.tschuchort.compiletesting

import com.google.devtools.ksp.processing.SymbolProcessor
import com.tschuchort.compiletesting.param.JvmCompilationModel
import com.tschuchort.compiletesting.step.CompilationStep
import com.tschuchort.compiletesting.step.KotlinJvmCompilationStep
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import java.io.File

class KspParameters {
    /**
     * The list of symbol processors for the kotlin compilation.
     * https://goo.gle/ksp
     */
    var symbolProcessors: List<SymbolProcessor> = emptyList()
    /**
     * Arbitrary arguments to be passed to ksp
     */
    val args: MutableMap<String, String> = mutableMapOf()
}

internal class KspModel(
    val jvm: JvmCompilationModel,
    val params: KspParameters
) {
    val baseDir = jvm.workingDir.resolve("ksp")
    val javaSourcesOutDir = baseDir.resolve("javaSrc")
    val kotlinSourceOutDir = baseDir.resolve("kotlinSrc")
    val resourceOutDir = baseDir.resolve("resources")
    val classes = baseDir.resolve("classes")
    val inputSources = baseDir.resolve("inputSources")

    fun mkdirs() {
        listOf(
            baseDir, javaSourcesOutDir, kotlinSourceOutDir, resourceOutDir, classes,
            inputSources
        ).forEach {
            it.deleteRecursively()
            it.mkdirs()
        }
    }
}

class KspCompilationStep : CompilationStep<JvmCompilationModel> {
    override val id: String
        get() = ID

    override fun execute(
        env: HostEnvironment,
        compilationUtils: KotlinCompilationUtils,
        model: JvmCompilationModel
    ): CompilationStep.IntermediateResult<JvmCompilationModel> {
        val kspParams = model.ksp ?: return CompilationStep.IntermediateResult.skip(model)
        if (kspParams.symbolProcessors.isEmpty()) {
            return CompilationStep.IntermediateResult.skip(model)
        }
        val kspModel = KspModel(
            jvm = model,
            params = kspParams
        )
        kspModel.mkdirs()
        val sources = model.sources.writeIfNeeded(kspModel.inputSources)
        val kspCompilationModel = ModelWithKspPlugin(kspModel)
        val args = compilationUtils.prepareCommonK2JVMArgs(
            params = kspCompilationModel,
            outputParams = KotlinCompilationUtils.JvmOutputParams(
                kspModel.classes
            )
        )
        val exitCode = compilationUtils.compileKotlin(
            env = env,
            params = kspCompilationModel,
            sources = sources,
            compiler = K2JVMCompiler(),
            arguments = args
        )
        val generatedSourceDirs = listOf(
            kspModel.javaSourcesOutDir, kspModel.kotlinSourceOutDir,
            kspModel.resourceOutDir
        )
        val generatedSources = generatedSourceDirs.flatMap {
            it.listFilesRecursively()
        }.map {
            SourceFile.fromPath(it)
        }
        return CompilationStep.IntermediateResult(
            exitCode = exitCode,
            updatedModel =ModelWithKspResult(
                delegate = model,
                generatedSources = generatedSources
            ),
            resultPayload = CompilationStep.ResultPayload(
                generatedSourceDirs = generatedSourceDirs,
                outputDirs = emptyList() // only report sources
            )
        )
    }

    companion object {
        val ID = "KSP"
    }
}

private class ModelWithKspPlugin(
    val kspModel: KspModel
) : JvmCompilationModel by kspModel.jvm {
    val kspRegistrar = KspCompileTestingComponentRegistrar(
        kspModel
    )
    override val compilerPlugins: List<ComponentRegistrar>
        get() {
            return kspModel.jvm.compilerPlugins + kspRegistrar
        }
}

private class ModelWithKspResult(
    val delegate: JvmCompilationModel,
    val generatedSources: List<SourceFile>
) : JvmCompilationModel by delegate {
    val combinedSources = delegate.sources + generatedSources
    override val sources: List<SourceFile>
        get() = combinedSources
}

private val JvmCompilationModel.ksp: KspParameters?
    get() = this.getExtensionData(KspParameters::class)

val KotlinCompilation.ksp: KspParameters
    get() = this.model().getOrPutExtensionData(KspParameters::class) {
        KspParameters().also {
            this@ksp.registerStep(
                step = KspCompilationStep(),
                shouldRunBefore = setOf(
                    KotlinJvmCompilationStep.ID
                ))
        }
    }