/**
 * Adds support for KSP (https://goo.gle/ksp).
 */
package com.tschuchort.compiletesting

import org.jetbrains.kotlin.ksp.KotlinSymbolProcessingCommandLineProcessor
import org.jetbrains.kotlin.ksp.KotlinSymbolProcessingComponentRegistrar
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import java.io.File

private const val KSP_PLUGIN_ID = "org.jetbrains.kotlin.ksp"

private fun KotlinCompilation.initAndGetKspConfig(): KspConfiguration {
    val config = KspConfiguration(workingDir.resolve("ksp"))
    if (config.workingDir.exists()) {
        // already configured, just return
        return config
    }
    config.classesOutDir.mkdirs()
    config.sourcesOurDir.mkdirs()
    config.syntheticSources.mkdirs()
    val kspOptions = listOf(
        PluginOption(KSP_PLUGIN_ID, "apclasspath", config.syntheticSources.path),
        PluginOption(KSP_PLUGIN_ID, "classes", config.classesOutDir.path),
        PluginOption(KSP_PLUGIN_ID, "sources", config.sourcesOurDir.path)
    )
    compilerPlugins = compilerPlugins + KotlinSymbolProcessingComponentRegistrar()
    commandLineProcessors = commandLineProcessors + listOf(KotlinSymbolProcessingCommandLineProcessor())
    pluginOptions = pluginOptions + kspOptions
    TODO("how do we add these additional classes, maybe generate byte-code ?")
    return config
}

fun KotlinCompilation.symbolProcessors(
    vararg processors: Class<out SymbolProcessor>
) = symbolProcessors(processors.map {
    it.typeName
})

fun KotlinCompilation.symbolProcessors(
    vararg processors: SymbolProcessor
) {
    val config = initAndGetKspConfig()
    // create a delegating SymbolProcessor class for each one of them
    val syntheticTypeNames = processors.map {
        val syntheticProcessor = DelegatingProcessor.synthesizeProcessorCode(it)
        config.syntheticSources.resolve(syntheticProcessor.relativePath).apply {
            parentFile.mkdirs()
            writeText(
                syntheticProcessor.code
            )
        }
        syntheticProcessor.classTypeName
    }
    symbolProcessors(syntheticTypeNames)
}

private fun KotlinCompilation.symbolProcessors(
    processorTypeNames: List<String>
) {
    check(processorTypeNames.isNotEmpty()) {
        "Must provide at least 1 symbol processor"
    }
    val config = initAndGetKspConfig()
    // create a fake classpath that references our symbol processor
    config.syntheticSources.apply {
        resolve("META-INF/services/org.jetbrains.kotlin.ksp.processing.SymbolProcessor").apply {
            parentFile.mkdirs()
            val contents = if (exists()) {
                this.readLines(Charsets.UTF_8)
            } else {
                emptyList()
            }
            val processorNames = processorTypeNames + contents
            writeText(
                processorNames.joinToString(System.lineSeparator())
            )
        }
    }
}

private data class KspConfiguration(
    val workingDir: File
) {
    val classesOutDir = workingDir.resolve("classesOutput")
    val sourcesOurDir = workingDir.resolve("sourcesOutput")
    val syntheticSources = workingDir.resolve("synthetic-ksp-service")
}
