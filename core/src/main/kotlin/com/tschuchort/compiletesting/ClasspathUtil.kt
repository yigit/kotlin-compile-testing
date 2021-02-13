package com.facebook.buck.jvm.java.javax.com.tschuchort.compiletesting

import com.google.common.base.Splitter
import com.google.common.base.StandardSystemProperty
import io.github.classgraph.ClassGraph
import java.io.File
import java.net.URL
import java.net.URLClassLoader


object ClasspathUtil {
    val platformClassLoader by lazy {
        try {
            // JDK >= 9
            ClassLoader::class.java.getMethod("getPlatformClassLoader").invoke(null) as ClassLoader
        } catch (e: ReflectiveOperationException) {
            // Java <= 8
            null
        }
    }
    val cachedClasspath by lazy {
        getClasspathFromClassloader(ClasspathUtil::class.java.classLoader)
    }

    val cachedClassGraphClasspath by lazy {
        getHostClasspathsFromClassGraph()
    }

    fun getHostClasspath(
        useGoogle: Boolean,
        useCached: Boolean
    ) : List<File> {
        return if(useGoogle) {
            if (useCached) {
                cachedClasspath
            } else {
                getClasspathFromClassloader(ClasspathUtil::class.java.classLoader)
            }
        } else {
            if (useCached) {
                cachedClassGraphClasspath
            } else {
                getHostClasspathsFromClassGraph()
            }
        }
    }

    private fun getHostClasspathsFromClassGraph(): List<File> {
        val classGraph = ClassGraph()
            .enableSystemJarsAndModules()
            .removeTemporaryFilesAfterScan()

        val classpaths = classGraph.classpathFiles
        val modules = classGraph.modules.mapNotNull { it.locationFile }

        return (classpaths + modules).distinctBy(File::getAbsolutePath)
    }

    /**
     * Returns the current classpaths of the given classloader including its parents.
     *
     * @throws IllegalArgumentException if the given classloader had classpaths which we could not
     * determine or use for compilation.
     */
    private fun getClasspathFromClassloader(currentClassloader: ClassLoader): List<File> {
        var currentClassloader = currentClassloader
        val systemClassLoader = ClassLoader.getSystemClassLoader()

        // Concatenate search paths from all classloaders in the hierarchy 'till the system classloader.
        val classpaths: MutableSet<String> = LinkedHashSet()
        while (true) {
            if (currentClassloader === systemClassLoader) {
                classpaths.addAll(
//                    Splitter.on(StandardSystemProperty.PATH_SEPARATOR.value())
//                        .split(StandardSystemProperty.JAVA_CLASS_PATH.value())
                    System.getProperty("java.class.path").split(
                        System.getProperty("path.separator")
                    )

                )
                break
            }
            if (currentClassloader === platformClassLoader) {
                break
            }
            if (currentClassloader is URLClassLoader) {
                // We only know how to extract classpaths from URLClassloaders.
                currentClassloader.urLs.forEach { url: URL ->
                    if (url.protocol == "file") {
                        classpaths.add(url.path)
                    } else {
                        throw IllegalArgumentException(
                            "Given classloader consists of classpaths which are "
                                    + "unsupported for compilation."
                        )
                    }
                }
            } else {
                throw IllegalArgumentException(
                    String.format(
                        (
                                "Classpath for compilation could not be extracted "
                                        + "since %s is not an instance of URLClassloader"),
                        currentClassloader
                    )
                )
            }
            currentClassloader = currentClassloader.getParent()
        }
        return classpaths.map { File(it) }
    }
}