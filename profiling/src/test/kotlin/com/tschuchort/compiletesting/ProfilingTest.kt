package com.tschuchort.compiletesting

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.cli.common.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Stopwatch
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import kotlin.time.ExperimentalTime

@ExperimentalTime
@RunWith(Parameterized::class)
class ProfilingTest(
    private val testConfiguration: TestConfiguration
) {
    @get:Rule
    val stopwatch = object: Stopwatch() {
        override fun finished(nanos: Long, description: Description) {
            timeTracking.record(description, testConfiguration, nanos)
            println("test ${description.methodName} took ${TimeUnit.NANOSECONDS.toMillis(nanos)}")
        }
    }

    @Before
    fun setCompilerCache() {
        val existingValue:String? = System.getProperty(KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY)
        System.setProperty(
            KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY,
            if (testConfiguration.useCachedCompilerEnv) "true" else "false"
        )
    }

    private fun newKotlinCompilation() = KotlinCompilation().apply {
        when (testConfiguration.processorType) {
            ProcessorType.KAPT -> annotationProcessors = listOf(DoNothingKapt())
            ProcessorType.KSP -> symbolProcessors = listOf(DonothingKsp())
        }
        verbose = false
    }

    @Test
    fun emptyKotlinFile() {
        newKotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("Foo.kt", ""))
        }.compile()
    }

    @Test
    fun emptyKotlinFile_withInheritedClasspath() {
        newKotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("Foo.kt", ""))
            inheritClassPath = true
        }.compile()
    }

    val javaSources = (0..5).map {
        SourceFile.java("Java_$it.java", """
            class Java_$it {}
        """.trimIndent())
    }
    val kotlinSources = (0..5).map {
        SourceFile.kotlin("Kotlin_$it.kt", """
            class Kotlin_$it {}
        """.trimIndent())
    }
    @Test
    fun manySources() {
        newKotlinCompilation().apply {
            sources = javaSources + kotlinSources
        }.compile()
    }

    @Test
    fun manySources_inheritClasspath() {
        newKotlinCompilation().apply {
            sources = javaSources + kotlinSources
            inheritClassPath = true
        }.compile()
    }

    class DoNothingKapt : AbstractProcessor() {
        override fun process(p0: MutableSet<out TypeElement>?, p1: RoundEnvironment?): Boolean {
            return true
        }
    }

    class DonothingKsp : SymbolProcessor {
        override fun finish() {
        }

        override fun init(
            options: Map<String, String>,
            kotlinVersion: KotlinVersion,
            codeGenerator: CodeGenerator,
            logger: KSPLogger
        ) {
        }

        override fun process(resolver: Resolver) {
        }
    }

    enum class ProcessorType {
        KSP,
        KAPT
    }

    data class TestConfiguration(
        val processorType: ProcessorType,
        val useCachedCompilerEnv: Boolean
    ) {

    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params() = (0 until 30).flatMap {
            listOf(
                TestConfiguration(
                    processorType = ProcessorType.KAPT,
                    useCachedCompilerEnv = false
                )
            )
        }.flatMap {
            listOf(it, it.copy(useCachedCompilerEnv = true))
        }

        @get:ClassRule
        @JvmStatic
        val timeTracking = TimeTracking()
    }
}