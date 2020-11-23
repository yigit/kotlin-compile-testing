package com.tschuchort.compiletesting

import io.github.classgraph.ClassGraph
import org.jetbrains.kotlin.cli.jvm.plugins.ServiceLoaderLite
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import java.io.File
import java.net.URI
import java.nio.file.Paths

class HostEnvironment(
    val messageStream: MessageStream
) {
    val hostClasspaths by lazy { findHostClasspaths() }
    /** Tries to find a file matching the given [regex] in the host process' classpath */
    fun findInHostClasspath(simpleName: String, regex: Regex): File? {
        val jarFile = hostClasspaths.firstOrNull { classpath ->
            classpath.name.matches(regex)
            //TODO("check that jar file actually contains the right classes")
        }

        if (jarFile == null)
            messageStream.log("Searched host classpaths for $simpleName and found no match")
        else
            messageStream.log("Searched host classpaths for $simpleName and found ${jarFile.path}")

        return jarFile
    }

    /** Returns the files on the classloader's classpath and modulepath */
    private fun findHostClasspaths(): List<File> {
        val classGraph = ClassGraph()
            .enableSystemJarsAndModules()
            .removeTemporaryFilesAfterScan()

        val classpaths = classGraph.classpathFiles
        val modules = classGraph.modules.mapNotNull { it.locationFile }

        return (classpaths + modules).distinctBy(File::getAbsolutePath)
    }

    fun getResourcesPath(): String {
        val resourceName = "META-INF/services/org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar"
        return this::class.java.classLoader.getResources(resourceName)
            .asSequence()
            .mapNotNull { url ->
                val uri = URI.create(url.toString().removeSuffix("/$resourceName"))
                when (uri.scheme) {
                    "jar" -> Paths.get(URI.create(uri.schemeSpecificPart.removeSuffix("!")))
                    "file" -> Paths.get(uri)
                    else -> return@mapNotNull null
                }.toAbsolutePath()
            }
            .find { resourcesPath ->
                ServiceLoaderLite.findImplementations(ComponentRegistrar::class.java, listOf(resourcesPath.toFile()))
                    .any { implementation -> implementation == MainComponentRegistrar::class.java.name }
            }?.toString() ?: throw AssertionError("Could not get path to ComponentRegistrar service from META-INF")
    }
}