package com.tschuchort.compiletesting

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.verify
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import io.github.classgraph.ClassGraph
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.ksp.KotlinSymbolProcessingCommandLineProcessor
import org.jetbrains.kotlin.ksp.KotlinSymbolProcessingComponentRegistrar
import org.jetbrains.kotlin.ksp.KspOptions
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class CompilerPluginsTest {
    @Rule
    @JvmField val temporaryFolder = TemporaryFolder()
    @Test
    fun `when compiler plugins are added they get executed`() {

        val mockPlugin = Mockito.mock(ComponentRegistrar::class.java)

        val result = defaultCompilerConfig().apply {
            sources = listOf(SourceFile.new("emptyKotlinFile.kt", ""))
            compilerPlugins = listOf(mockPlugin)
            inheritClassPath = true
        }.compile()

        verify(mockPlugin, atLeastOnce()).registerProjectComponents(any(), any())

        assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    }

    @Test
    fun `when compiler plugins and annotation processors are added they get executed`() {

        val annotationProcessor = object : AbstractProcessor() {
            override fun getSupportedAnnotationTypes(): Set<String> = setOf(ProcessElem::class.java.canonicalName)

            override fun process(p0: MutableSet<out TypeElement>?, p1: RoundEnvironment?): Boolean {
                p1?.getElementsAnnotatedWith(ProcessElem::class.java)?.forEach {
                    Assert.assertEquals("JSource", it?.simpleName.toString())
                }
                return false
            }
        }

        val mockPlugin = Mockito.mock(ComponentRegistrar::class.java)

        val jSource = SourceFile.kotlin(
            "JSource.kt", """
				package com.tschuchort.compiletesting;

				@ProcessElem
				class JSource {
					fun foo() { }
				}
					"""
        )

        val result = defaultCompilerConfig().apply {
            sources = listOf(jSource)
            annotationProcessors = listOf(annotationProcessor)
            compilerPlugins = listOf(mockPlugin)
            inheritClassPath = true
        }.compile()

        verify(mockPlugin, atLeastOnce()).registerProjectComponents(any(), any())

        assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    }

    @Test
    fun `KSP processor is invoked`() {
        val kspProcessor = KspTestProcessor()
        val kSource = SourceFile.kotlin(
            "KSource.kt", """
				package com.tschuchort.compiletesting
			
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
            inheritClassPath = false
            pluginOptions = kspOptions
            commandLineProcessors = listOf(KotlinSymbolProcessingCommandLineProcessor())
        }.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    }
}