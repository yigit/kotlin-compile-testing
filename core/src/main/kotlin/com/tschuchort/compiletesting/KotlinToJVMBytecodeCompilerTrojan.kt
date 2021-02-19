package com.tschuchort.compiletesting

import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.modules.Module
import java.io.File
import java.lang.reflect.Method

private val configureSourceRootsMethod : Method by lazy {
    KotlinToJVMBytecodeCompiler::class.java.methods.first {
        it.name == "configureSourceRoots\$cli"
    }
}

private val compileModulesMethod : Method by lazy {
    KotlinToJVMBytecodeCompiler::class.java.methods.first {
        it.name == "compileModules\$cli"
    }
}

fun KotlinToJVMBytecodeCompiler.pleaseConfigureSourceRoots(
    configuration: CompilerConfiguration, chunk: MutableList<Module>, buildFile: File? = null
) {
    configureSourceRootsMethod.invoke(KotlinToJVMBytecodeCompiler,
        configuration, chunk, buildFile
    )
}

fun KotlinToJVMBytecodeCompiler.pleaseCompileModules(
    environment: KotlinCoreEnvironment,
    buildFile: File?,
    chunk: MutableList<Module>
) {
    val method = compileModulesMethod
    method.invoke(KotlinToJVMBytecodeCompiler, environment, buildFile, chunk, false)
}

