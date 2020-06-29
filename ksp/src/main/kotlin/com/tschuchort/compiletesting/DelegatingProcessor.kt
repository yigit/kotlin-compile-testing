package com.tschuchort.compiletesting

import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import java.util.*

abstract class DelegatingProcessor(
    private val delegateId: String
) : SymbolProcessor {
    val delegate by lazy {
        registry.get(delegateId) ?: error("DelegatingProcessor is not initialized properly")
    }

    override fun finish() {
        return delegate.finish()
    }

    override fun init(options: Map<String, String>, kotlinVersion: KotlinVersion, codeGenerator: CodeGenerator) {
        delegate.init(options, kotlinVersion, codeGenerator)
    }

    override fun process(resolver: Resolver) {
        delegate.process(resolver)
    }

    class SyntheticProcessor(
        val classTypeName:String,
        val relativePath:String,
        val code : String
    )

    companion object {
        private const val GENERATED_DELEGATE_PACKAGE = "com.tschuchort.compiletesting.generated.delegate"
        private val registry = mutableMapOf<String, SymbolProcessor>()

        fun synthesizeProcessorCode(delegate:SymbolProcessor) : SyntheticProcessor {
            val id = UUID.randomUUID().toString().replace('-', '_')
            registry[id] = delegate
            val className = "GeneratedDelegate_$id"
            val code = """
                package $GENERATED_DELEGATE_PACKAGE
                import ${DelegatingProcessor::class.java.canonicalName}
                class $className : ${DelegatingProcessor::class.java.simpleName}(\"$id\")
            """.trimIndent()
            return SyntheticProcessor(
                classTypeName = "$GENERATED_DELEGATE_PACKAGE.$className",
                relativePath = GENERATED_DELEGATE_PACKAGE.replace('.', '/') + "." + className + ".kt",
                code = code
            )
        }

        fun clear() {
            registry.clear()
        }
    }
}