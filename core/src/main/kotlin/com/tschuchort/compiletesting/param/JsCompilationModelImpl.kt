package com.tschuchort.compiletesting.param

import com.tschuchort.compiletesting.default
import com.tschuchort.compiletesting.kotlinDependencyRegex
import java.io.File

internal class JsCompilationModelImpl : CompilationModelImpl(), JsCompilationModel {
    override var outputFileName: String = "test.js"

    /**
     * Generate unpacked KLIB into parent directory of output JS file. In combination with -meta-info
     * generates both IR and pre-IR versions of library.
     */
    override var irProduceKlibDir: Boolean = false

    /** Generate packed klib into file specified by -output. Disables pre-IR backend */
    override var irProduceKlibFile: Boolean = false

    /** Generates JS file using IR backend. Also disables pre-IR backend */
    override var irProduceJs: Boolean = false

    /** Perform experimental dead code elimination */
    override var irDce: Boolean = false

    /** Perform a more experimental faster dead code elimination */
    override var irDceDriven: Boolean = false

    /** Print declarations' reachability info to stdout during performing DCE */
    override var irDcePrintReachabilityInfo: Boolean = false

    /** Disables pre-IR backend */
    override var irOnly: Boolean = false

    /** Specify a compilation module name for IR backend */
    override var irModuleName: String? = null

    /**
     * Path to the kotlin-stdlib-js.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    override var kotlinStdLibJsJar: File? by default {
        environment.findInHostClasspath("kotlin-stdlib-js.jar",
            kotlinDependencyRegex("kotlin-stdlib-js")
        )
    }

    // *.class files, Jars and resources (non-temporary) that are created by the
    // compilation will land here
    override val outputDir get() = workingDir.resolve("output")
}