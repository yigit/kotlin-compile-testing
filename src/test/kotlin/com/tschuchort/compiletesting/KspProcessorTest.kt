package com.tschuchort.compiletesting

import org.assertj.core.api.Assertions
import org.jetbrains.kotlin.ksp.KotlinSymbolProcessingCommandLineProcessor
import org.jetbrains.kotlin.ksp.KotlinSymbolProcessingComponentRegistrar
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KspProcessorTest {
    @Rule
    @JvmField val temporaryFolder = TemporaryFolder()
    @Test
    fun `KSP processor is invoked`() {
        val kSource = SourceFile.kotlin(
            "KSource.kt", """
				package com.tschuchort.compiletesting
			    import com.example.generated.Generated // access generated code
				@ProcessElem
				class KSource {
					fun foo() {}
				}
				"""
        )
        // create a fake classpath that references our symbol processor
        val processorClasspath = temporaryFolder.newFolder("symbol-processor").apply {
            resolve("META-INF/services/org.jetbrains.kotlin.ksp.processing.SymbolProcessor").apply {
                parentFile.mkdirs()
                writeText(KspTestProcessor::class.java.canonicalName)
            }
        }
        val kspOptions = listOf(
            PluginOption("org.jetbrains.kotlin.ksp", "apclasspath", processorClasspath.path),
            PluginOption("org.jetbrains.kotlin.ksp", "classes", temporaryFolder.newFolder("outClasses").path),
            PluginOption("org.jetbrains.kotlin.ksp", "sources", temporaryFolder.newFolder("outSources").path)
        )


        val result = defaultCompilerConfig().apply {
            sources = listOf(kSource)
            annotationProcessors = emptyList()
            compilerPlugins = listOf(KotlinSymbolProcessingComponentRegistrar())
            inheritClassPath = true
            pluginOptions = kspOptions
            commandLineProcessors = listOf(KotlinSymbolProcessingCommandLineProcessor())
        }.compile()
        Assertions.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

}