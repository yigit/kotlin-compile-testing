package com.facebook.buck.jvm.java.javax.com.tschuchort.compiletesting

import java.io.File

interface JsCompilationModel {
    var outputFileName: String

    /**
 * Generate unpacked KLIB into parent directory of output JS file. In combination with -meta-info
 * generates both IR and pre-IR versions of library.
 */
var irProduceKlibDir: Boolean

    /** Generate packed klib into file specified by -output. Disables pre-IR backend */
var irProduceKlibFile: Boolean

    /** Generates JS file using IR backend. Also disables pre-IR backend */
var irProduceJs: Boolean

    /** Perform experimental dead code elimination */
var irDce: Boolean

    /** Perform a more experimental faster dead code elimination */
var irDceDriven: Boolean

    /** Print declarations' reachability info to stdout during performing DCE */
var irDcePrintReachabilityInfo: Boolean

    /** Disables pre-IR backend */
var irOnly: Boolean

    /** Specify a compilation module name for IR backend */
var irModuleName: String?

    /**
 * Path to the kotlin-stdlib-js.jar
 * If none is given, it will be searched for in the host
 * process' classpaths
 */
var kotlinStdLibJsJar: File?

    // *.class files, Jars and resources (non-temporary) that are created by the
// compilation will land here
val outputDir: File
}