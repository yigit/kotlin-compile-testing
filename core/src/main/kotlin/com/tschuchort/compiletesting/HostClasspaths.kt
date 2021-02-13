package com.facebook.buck.jvm.java.javax.com.tschuchort.compiletesting

import com.tschuchort.compiletesting.*
import com.tschuchort.compiletesting.findToolsJarFromJdk
import com.tschuchort.compiletesting.getJavaHome
import com.tschuchort.compiletesting.isJdk9OrLater
import com.tschuchort.compiletesting.kotlinDependencyRegex
import java.io.File

interface HostClasspaths {
    val all: List<File>

    companion object {
        private val CACHED_GOOGLE by lazy {
            HostClasspathsImpl(ClasspathUtil.getHostClasspath(
                useGoogle = true,
                useCached = true
            ))
        }
        private val CACHED_CLASSGRAPH by lazy {
            HostClasspathsImpl(ClasspathUtil.getHostClasspath(
                useGoogle = false,
                useCached = true
            ))
        }

        internal fun getInstance(
            useGoogle: Boolean,
            useCached: Boolean
        ) : HostClasspaths = if(useCached) {
            if (useGoogle) CACHED_GOOGLE
            else CACHED_CLASSGRAPH
        } else {
            HostClasspathsImpl(ClasspathUtil.getHostClasspath(
                useGoogle = useGoogle,
                useCached = false
            ))
        }
    }

    val kotlinStdLibCommonJar: File?
    val kotlinStdLibJar: File?
    val kotlinStdLibJdkJar: File?
    val kotlinReflectJar: File?
    val kotlinScriptRuntimeJar: File?
    val jdkHome: File?
    fun getToolsJar(jdkHome: File?): File?
    val kotlinStdLibJsJar: File?
}

private class HostClasspathsImpl(
    override val all: List<File>
) : HostClasspaths {
    override val jdkHome by lazy {
        if(isJdk9OrLater())
            getJavaHome()
        else
            getJavaHome().parentFile
    }

    private val cachedToolsJar by lazy {
        findToolsJar(jdkHome)
    }
    private fun findToolsJar(jdkHome: File?): File? {
        return if (!isJdk9OrLater())
            jdkHome?.let { findToolsJarFromJdk(it) }
                ?: findInHostClasspath(all, "tools.jar", Regex("tools.jar"))
        else
            null
    }
    override fun getToolsJar(jdkHome: File?): File? {
        return if (jdkHome === this.jdkHome) {
            cachedToolsJar
        } else {
            findToolsJar(jdkHome)
        }
    }

    override val kotlinStdLibJsJar: File? by lazy {
        findInHostClasspath(all, "kotlin-stdlib-js.jar",
            kotlinDependencyRegex("kotlin-stdlib-js"))
    }

    override val kotlinScriptRuntimeJar: File? by lazy {
        findInHostClasspath(all, "kotlin-script-runtime.jar",
            kotlinDependencyRegex("kotlin-script-runtime"))
    }
    override val kotlinReflectJar: File? by lazy {
        findInHostClasspath(all, "kotlin-reflect.jar",
            kotlinDependencyRegex("kotlin-reflect"))
    }
    override val kotlinStdLibJdkJar: File? by lazy {
        findInHostClasspath(all, "kotlin-stdlib-jdk*.jar",
            kotlinDependencyRegex("kotlin-stdlib-jdk[0-9]+"))
    }
    override val kotlinStdLibJar: File? by lazy {
        findInHostClasspath(all, "kotlin-stdlib.jar",
            kotlinDependencyRegex("(kotlin-stdlib|kotlin-runtime)"))
    }
    override val kotlinStdLibCommonJar: File? by lazy {
        findInHostClasspath(all, "kotlin-stdlib-common.jar",
            kotlinDependencyRegex("kotlin-stdlib-common")
        )
    }
    /** Tries to find a file matching the given [regex] in the host process' classpath */
    private fun findInHostClasspath(hostClasspaths: List<File>, simpleName: String, regex: Regex): File? {
        val jarFile = hostClasspaths.firstOrNull { classpath ->
            classpath.name.matches(regex)
            //TODO("check that jar file actually contains the right classes")
        }
        return jarFile
    }

}