package com.tschuchort.compiletesting.step

import com.facebook.buck.jvm.java.javax.SynchronizedToolProvider
import com.tschuchort.compiletesting.*
import com.tschuchort.compiletesting.param.JvmCompilationModel
import java.io.File
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject

private class JavacParams(
    private val compilationParams: JvmCompilationModel
) {
    val baseDir get() = compilationParams.workingDir.resolve("javac")
    val sourceDir get() = baseDir.resolve("src")
    val classesDir get() = baseDir.resolve("classes")
    fun mkdirs() {
        listOf(baseDir, sourceDir, classesDir).forEach {
            // TODO create a helper function for this.
            it.deleteRecursively()
            it.mkdirs()
        }
    }
}

class JavacCompilationStep : CompilationStep<JvmCompilationModel> {
    override val id: String
        get() = ID

    override fun execute(
        env: CompilationEnvironment,
        compilationUtils: KotlinCompilationUtils,
        model: JvmCompilationModel
    ): CompilationStep.IntermediateResult<JvmCompilationModel> {
        val javacParams = JavacParams(model)
        javacParams.mkdirs()
        val exitCode = compileJava(
            env = env,
            params = model,
            compilationUtils = compilationUtils,
            javacParams = javacParams
        )
        return CompilationStep.IntermediateResult(
            exitCode = exitCode,
            updatedModel = OutputCompilationModel(
                delegate = model,
                javacClassesDir = javacParams.classesDir
            ),
            outputFolders = listOf(javacParams.classesDir)
        )
    }

    /** Performs the 4th compilation step to compile Java source files */
    private fun compileJava(
        env: CompilationEnvironment,
        params: JvmCompilationModel,
        compilationUtils: KotlinCompilationUtils,
        javacParams: JavacParams
    ): KotlinCompilation.ExitCode {
        val sourceFiles = params.sources.writeIfNeeded(javacParams.sourceDir)
        val javaSources = sourceFiles
            .filterNot(File::hasKotlinFileExtension)

        if(javaSources.isEmpty()) {
            return KotlinCompilation.ExitCode.OK
        }

        if(params.jdkHome != null) {
            /* If a JDK home is given, try to run javac from there so it uses the same JDK
               as K2JVMCompiler. Changing the JDK of the system java compiler via the
               "--system" and "-bootclasspath" options is not so easy. */

            val jdkBinFile = File(params.jdkHome, "bin")
            check(jdkBinFile.exists()) { "No JDK bin folder found at: ${jdkBinFile.toPath()}" }

            val javacCommand = jdkBinFile.absolutePath + File.separatorChar + "javac"
            val isJavac9OrLater = isJavac9OrLater(getJavacVersionString(javacCommand))
            val javacArgs = baseJavacArgs(
                params = params,
                javacParams = javacParams,
                compilationUtils = compilationUtils,
                isJavac9OrLater = isJavac9OrLater)

            val javacProc = ProcessBuilder(listOf(javacCommand) + javacArgs + javaSources.map(File::getAbsolutePath))
                .directory(javacParams.baseDir) // base dir OK?
                .redirectErrorStream(true)
                .start()

            env.messageStream.captureProcessoutputs(javacProc)

            return when(javacProc.waitFor()) {
                0 -> KotlinCompilation.ExitCode.OK
                1 -> KotlinCompilation.ExitCode.COMPILATION_ERROR
                else -> KotlinCompilation.ExitCode.INTERNAL_ERROR
            }
        }
        else {
            /*  If no JDK is given, we will use the host process' system java compiler
                and erase the bootclasspath. The user is then on their own to somehow
                provide the JDK classes via the regular classpath because javac won't
                work at all without them */

            val isJavac9OrLater = isJdk9OrLater()
            val javacArgs = baseJavacArgs(
                params = params,
                compilationUtils = compilationUtils,
                javacParams = javacParams,
                isJavac9OrLater = isJavac9OrLater
            ).apply {
                // erase bootclasspath or JDK path because no JDK was specified
                if (isJavac9OrLater)
                    addAll("--system", "none")
                else
                    addAll("-bootclasspath", "")
            }

            env.messageStream.log("jdkHome is null. Using system java compiler of the host process.")

            val javac = SynchronizedToolProvider.systemJavaCompiler
            val javaFileManager = javac.getStandardFileManager(null, null, null)
            val diagnosticCollector = DiagnosticCollector<JavaFileObject>()

            fun printDiagnostics() = diagnosticCollector.diagnostics.forEach { diag ->
                when(diag.kind) {
                    Diagnostic.Kind.ERROR -> env.messageStream.error(diag.getMessage(null))
                    Diagnostic.Kind.WARNING,
                    Diagnostic.Kind.MANDATORY_WARNING -> env.messageStream.warn(diag.getMessage(null))
                    else -> env.messageStream.log(diag.getMessage(null))
                }
            }

            try {
                val noErrors = javac.getTask(
                    env.messageStream.createWriter(), javaFileManager,
                    diagnosticCollector, javacArgs,
                    /* classes to be annotation processed */ null,
                    javaSources.map { FileJavaFileObject(it) }
                        .filter { it.kind == JavaFileObject.Kind.SOURCE }
                ).call()

                printDiagnostics()

                return if(noErrors)
                    KotlinCompilation.ExitCode.OK
                else
                    KotlinCompilation.ExitCode.COMPILATION_ERROR
            }
            catch (e: Exception) {
                if(e is RuntimeException || e is IllegalArgumentException) {
                    printDiagnostics()
                    env.messageStream.error(e.toString())
                    return KotlinCompilation.ExitCode.INTERNAL_ERROR
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
        params: JvmCompilationModel,
        compilationUtils: KotlinCompilationUtils,
        javacParams: JavacParams,
        isJavac9OrLater: Boolean
    ) = mutableListOf<String>().apply {
        if(params.verbose) {
            add("-verbose")
            add("-Xlint:path") // warn about invalid paths in CLI
            add("-Xlint:options") // warn about invalid options in CLI

            if(isJavac9OrLater)
                add("-Xlint:module") // warn about issues with the module system
        }

        addAll("-d", javacParams.classesDir.absolutePath)

        add("-proc:none") // disable annotation processing

        if(params.allWarningsAsErrors)
            add("-Werror")

        addAll(params.javacArguments)

        addAll("-cp", compilationUtils.commonClasspaths(params)
            .joinToString(File.pathSeparator, transform = File::getAbsolutePath))
    }

    private class OutputCompilationModel(
        val delegate: JvmCompilationModel,
        val javacClassesDir: File
    ) : JvmCompilationModel by delegate {
        override val classpaths: List<File>
            get() = delegate.classpaths + listOf(javacClassesDir)
    }

    companion object {
        val ID = "javac"
    }
}
