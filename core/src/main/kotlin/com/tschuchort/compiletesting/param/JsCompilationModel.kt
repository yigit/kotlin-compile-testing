package com.tschuchort.compiletesting.param

import java.io.File

interface JsCompilationModel : CompilationModel {
    val outputFileName: String

    /**
     * Generate unpacked KLIB into parent directory of output JS file. In combination with -meta-info
     * generates both IR and pre-IR versions of library.
     */
    val irProduceKlibDir: Boolean

    /** Generate packed klib into file specified by -output. Disables pre-IR backend */
    val irProduceKlibFile: Boolean

    /** Generates JS file using IR backend. Also disables pre-IR backend */
    val irProduceJs: Boolean

    /** Perform experimental dead code elimination */
    val irDce: Boolean

    /** Perform a more experimental faster dead code elimination */
    val irDceDriven: Boolean

    /** Print declarations' reachability info to stdout during performing DCE */
    val irDcePrintReachabilityInfo: Boolean

    /** Disables pre-IR backend */
    val irOnly: Boolean

    /** Specify a compilation module name for IR backend */
    val irModuleName: String?

    /**
     * Path to the kotlin-stdlib-js.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    val kotlinStdLibJsJar: File?
}