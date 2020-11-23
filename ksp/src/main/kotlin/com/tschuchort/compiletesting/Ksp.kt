/**
 * Adds support for KSP (https://goo.gle/ksp).
 */
package com.tschuchort.compiletesting

import com.google.devtools.ksp.AbstractKotlinSymbolProcessingExtension
import com.google.devtools.ksp.KspOptions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.impl.MessageCollectorBasedKSPLogger
import com.tschuchort.compiletesting.param.JvmCompilationModel
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.io.File

/**
 * Custom subclass of [AbstractKotlinSymbolProcessingExtension] where processors are pre-defined instead of being
 * loaded via ServiceLocator.
 */
private class KspTestExtension(
    options: KspOptions,
    private val processors: List<SymbolProcessor>,
    logger: KSPLogger
) : AbstractKotlinSymbolProcessingExtension(
    options = options,
    logger = logger,
    testMode = false
) {
    override fun loadProcessors() = processors
}

/**
 * Registers the [KspTestExtension] to load the given list of processors.
 */
internal class KspCompileTestingComponentRegistrar(
    private val kspModel: KspModel
) : ComponentRegistrar {
    val processors = kspModel.params.symbolProcessors

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        if (processors.isEmpty()) {
            return
        }
        val options = KspOptions.Builder().apply {
            this.processingOptions.putAll(kspModel.params.args)
            this.classOutputDir = kspModel.classes
            this.javaOutputDir = kspModel.javaSourcesOutDir
            this.kotlinOutputDir = kspModel.kotlinSourceOutDir
            this.resourceOutputDir = kspModel.resourceOutDir
        }.build()
        // TODO: replace with KotlinCompilation.internalMessageStream
        val kspLogger = MessageCollectorBasedKSPLogger(
            PrintingMessageCollector(System.err, MessageRenderer.GRADLE_STYLE, kspModel.jvm.verbose)
        )
        val registrar = KspTestExtension(
            options, processors, kspLogger
        )
        AnalysisHandlerExtension.registerExtension(project, registrar)
    }
}
