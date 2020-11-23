package com.tschuchort.compiletesting.param

import java.io.File

interface OutputParameters {
    /** Working directory for the compilation */
    val workingDir: File
}