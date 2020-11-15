package com.tschuchort.compiletesting

import com.facebook.buck.jvm.java.javax.SynchronizedToolProvider
import java.io.File
import java.io.OutputStreamWriter
import java.lang.RuntimeException
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject

class JavaCompilationStep : CompilationStep<KotlinCompilation> {
    override val order: CompilationStep.Order
        get() = CompilationStep.Order.POST_KOTLIN_COMPILATION

    override fun execute(compilation: KotlinCompilation, sourceFiles: List<File>): CompilationStep.IntermediateResult {
        val javaSources = sourceFiles
            .filterNot<File>(File::hasKotlinFileExtension)

        val model = compilation.model
        if(javaSources.isEmpty())
            return CompilationStep.IntermediateResult.SKIP
        val jdkHome = compilation.jdkHome
        if(jdkHome != null) {
            /* If a JDK home is given, try to run javac from there so it uses the same JDK
               as K2JVMCompiler. Changing the JDK of the system java compiler via the
               "--system" and "-bootclasspath" options is not so easy. */

            val jdkBinFile = File(jdkHome, "bin")
            check(jdkBinFile.exists()) { "No JDK bin folder found at: ${jdkBinFile.toPath()}" }

            val javacCommand = jdkBinFile.absolutePath + File.separatorChar + "javac"

            val isJavac9OrLater = isJavac9OrLater(getJavacVersionString(javacCommand))
            val javacArgs = baseJavacArgs(compilation, isJavac9OrLater)

            val javacProc = ProcessBuilder(listOf(javacCommand) + javacArgs + javaSources.map(File::getAbsolutePath))
                .directory(compilation.workingDir)
                .redirectErrorStream(true)
                .start()

            javacProc.inputStream.copyTo(model.internalMessageStream)
            javacProc.errorStream.copyTo(model.internalMessageStream)

            val exitCode = when(javacProc.waitFor()) {
                0 -> KotlinCompilation.ExitCode.OK
                1 -> KotlinCompilation.ExitCode.COMPILATION_ERROR
                else -> KotlinCompilation.ExitCode.INTERNAL_ERROR
            }
            return CompilationStep.IntermediateResult(
                exitCode = exitCode,
                generatedSources = emptyList()
            )
        }
        else {
            /*  If no JDK is given, we will use the host process' system java compiler
                and erase the bootclasspath. The user is then on their own to somehow
                provide the JDK classes via the regular classpath because javac won't
                work at all without them */

            val isJavac9OrLater = isJdk9OrLater()
            val javacArgs = baseJavacArgs(compilation, isJavac9OrLater).apply {
                // erase bootclasspath or JDK path because no JDK was specified
                if (isJavac9OrLater)
                    addAll("--system", "none")
                else
                    addAll("-bootclasspath", "")
            }

            model.log("jdkHome is null. Using system java compiler of the host process.")

            val javac = SynchronizedToolProvider.systemJavaCompiler
            val javaFileManager = javac.getStandardFileManager(null, null, null)
            val diagnosticCollector = DiagnosticCollector<JavaFileObject>()

            fun printDiagnostics() = diagnosticCollector.diagnostics.forEach { diag ->
                when(diag.kind) {
                    Diagnostic.Kind.ERROR -> error(diag.getMessage(null))
                    Diagnostic.Kind.WARNING,
                    Diagnostic.Kind.MANDATORY_WARNING -> model.warn(diag.getMessage(null))
                    else -> model.log(diag.getMessage(null))
                }
            }

            try {
                val noErrors = javac.getTask(
                    OutputStreamWriter(model.internalMessageStream), javaFileManager,
                    diagnosticCollector, javacArgs,
                    /* classes to be annotation processed */ null,
                    javaSources.map { FileJavaFileObject(it) }
                        .filter { it.kind == JavaFileObject.Kind.SOURCE }
                ).call()

                printDiagnostics()

                return if(noErrors) {
                    CompilationStep.IntermediateResult(
                        exitCode = KotlinCompilation.ExitCode.OK,
                        generatedSources = emptyList()
                    )
                }
                else {
                    CompilationStep.IntermediateResult(
                        exitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
                        generatedSources = emptyList()
                    )
                }
            }
            catch (e: Exception) {
                if(e is RuntimeException || e is IllegalArgumentException) {
                    printDiagnostics()
                    compilation.error(e.toString())
                    return CompilationStep.IntermediateResult(
                        exitCode = KotlinCompilation.ExitCode.INTERNAL_ERROR,
                        generatedSources = emptyList()
                    )
                }
                else
                    throw e
            }
        }
    }

    /**
     * 	Base javac arguments that only depend on the the arguments given by the user
     *  Depending on which compiler implementation is actually used, more arguments
     *  may be added
     */
    private fun baseJavacArgs(
        compilation: KotlinCompilation,
        isJavac9OrLater: Boolean) = mutableListOf<String>().apply {
        if(compilation.verbose) {
            add("-verbose")
            add("-Xlint:path") // warn about invalid paths in CLI
            add("-Xlint:options") // warn about invalid options in CLI

            if(isJavac9OrLater)
                add("-Xlint:module") // warn about issues with the module system
        }

        addAll("-d", compilation.classesDir.absolutePath)

        add("-proc:none") // disable annotation processing

        if(compilation.allWarningsAsErrors)
            add("-Werror")

        addAll(compilation.javacArguments)

        // also add class output path to javac classpath so it can discover
        // already compiled Kotlin classes
        addAll("-cp", (compilation.commonClasspaths() + compilation.classesDir)
            .joinToString(File.pathSeparator, transform = File::getAbsolutePath))
    }
}