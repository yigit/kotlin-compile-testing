package com.tschuchort.compiletesting

import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor

class KspTestProcessor : SymbolProcessor {
    lateinit var codeGenerator: CodeGenerator
    override fun finish() {
    }

    override fun init(options: Map<String, String>, kotlinVersion: KotlinVersion, codeGenerator: CodeGenerator) {
        this.codeGenerator = codeGenerator
    }

    override fun process(resolver: Resolver) {
        resolver.getSymbolsWithAnnotation(ProcessElem::class.java.canonicalName).forEach {
            codeGenerator.createNewFile("com.example.generated", "Generated").writeText("""
                package com.example.generated
                class Generated {}
            """.trimIndent())
        }
    }
}