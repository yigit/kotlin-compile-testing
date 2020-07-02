package com.tschuchort.compiletesting

import javassist.ClassPool
import javassist.CtNewConstructor
import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import java.io.File
import java.util.UUID
import kotlin.concurrent.getOrSet

/**
 * Utility to class to synthesize a one-off SymbolProcessor for instance SymbolPrcessors passed during compilation.
 */
object DelegatingProcessorGenerator {
    fun generate(
            outputDir: File,
            delegate: SymbolProcessor
    ): String {
        val id = UUID.randomUUID().toString()
        val delegateQName = "Synthetic${id.replace("-", "_")}"
        val pool = ClassPool.getDefault()
        val baseClass = pool.get(DelegatingSymbolProcessor::class.java.canonicalName!!)
        val newDelegate = pool.makeClass(delegateQName, baseClass)
        val code = """{super("$id");}"""
        val constructor = CtNewConstructor
                .make(emptyArray(), emptyArray(), code, newDelegate)
        newDelegate.addConstructor(constructor)
        newDelegate.writeFile(outputDir.absolutePath)
        DelegatingSymbolProcessor.addMapping(id, delegate)
        return delegateQName
    }
}

internal abstract class DelegatingSymbolProcessor(val id: String) : SymbolProcessor {
    val delegate = mapping.get()[id] ?: error("cannot find the delegate")
    override fun finish() {
        delegate.finish()
    }

    override fun init(options: Map<String, String>, kotlinVersion: KotlinVersion, codeGenerator: CodeGenerator) {
        delegate.init(options, kotlinVersion, codeGenerator)
    }

    override fun process(resolver: Resolver) {
        delegate.process(resolver)
    }

    companion object {
        private val mapping = ThreadLocal<MutableMap<String, SymbolProcessor>>()

        fun addMapping(id: String, processor: SymbolProcessor) {
            mapping.getOrSet {
                mutableMapOf()
            }.set(id, processor)
        }
    }
}