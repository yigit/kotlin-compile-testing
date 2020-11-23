package com.tschuchort.compiletesting.param

import com.tschuchort.compiletesting.default
import com.tschuchort.compiletesting.findToolsJarFromJdk
import com.tschuchort.compiletesting.getJdkHome
import com.tschuchort.compiletesting.isJdk9OrLater
import com.tschuchort.compiletesting.kotlinDependencyRegex
import org.jetbrains.kotlin.config.JVMAssertionsMode
import org.jetbrains.kotlin.config.JvmDefaultMode
import org.jetbrains.kotlin.config.JvmTarget
import java.io.File
import java.nio.file.Path

internal class JvmCompilationModelImpl : CompilationModelImpl(), JvmCompilationModel {
    var x:Boolean = true
    override var includeRuntime: Boolean = false

    /** Make kapt correct error types */
    override var correctErrorTypes: Boolean = true

    /** Name of the generated .kotlin_module file */
    override var moduleName: String? = null

    /** Target version of the generated JVM bytecode */
    override var jvmTarget: String = JvmTarget.DEFAULT.description

    /** Generate metadata for Java 1.8 reflection on method parameters */
    override var javaParameters: Boolean = false

    /** Use the IR backend */
    override var useIR: Boolean = false

    /** Paths where to find Java 9+ modules */
    override var javaModulePath: Path? = null

    /**
     * Root modules to resolve in addition to the initial modules,
     * or all modules on the module path if <module> is ALL-MODULE-PATH
     */
    override var additionalJavaModules: MutableList<File> = mutableListOf()

    /** Don't generate not-null assertions for arguments of platform types */
    override var noCallAssertions: Boolean = false

    /** Don't generate not-null assertion for extension receiver arguments of platform types */
    override var noReceiverAssertions: Boolean = false

    /** Don't generate not-null assertions on parameters of methods accessible from Java */
    override var noParamAssertions: Boolean = false

    /** Generate nullability assertions for non-null Java expressions */
    override var strictJavaNullabilityAssertions: Boolean = false

    /** Disable optimizations */
    override var noOptimize: Boolean = false

    /**
     * Normalize constructor calls (disable: don't normalize; enable: normalize),
     * default is 'disable' in language version 1.2 and below, 'enable' since language version 1.3
     *
     * {disable|enable}
     */
    override var constructorCallNormalizationMode: String? = null

    /** Assert calls behaviour {always-enable|always-disable|jvm|legacy} */
    override var assertionsMode: String? = JVMAssertionsMode.DEFAULT.description

    /** Path to the .xml build file to compile */
    override var buildFile: File? = null

    /** Compile multifile classes as a hierarchy of parts and facade */
    override var inheritMultifileParts: Boolean = false

    /** Use type table in metadata serialization */
    override var useTypeTable: Boolean = false

    /** Allow Kotlin runtime libraries of incompatible versions in the classpath */
    override var skipRuntimeVersionCheck: Boolean = false

    /** Path to JSON file to dump Java to Kotlin declaration mappings */
    override var declarationsOutputPath: File? = null

    /** Combine modules for source files and binary dependencies into a single module */
    override var singleModule: Boolean = false

    /** Suppress the \"cannot access built-in declaration\" error (useful with -no-stdlib) */
    override var suppressMissingBuiltinsError: Boolean = false

    /** Script resolver environment in key-value pairs (the value could be quoted and escaped) */
    override var scriptResolverEnvironment: MutableMap<String, String> = mutableMapOf()

    /** Java compiler arguments */
    override var javacArguments: MutableList<String> = mutableListOf()

    /** Package prefix for Java files */
    override var javaPackagePrefix: String? = null

    /**
     * Specify behavior for Checker Framework compatqual annotations (NullableDecl/NonNullDecl).
     * Default value is 'enable'
     */
    override var supportCompatqualCheckerFrameworkAnnotations: String? = null

    /** Do not throw NPE on explicit 'equals' call for null receiver of platform boxed primitive type */
    override var noExceptionOnExplicitEqualsForBoxedNull: Boolean = false

    /** Allow to use '@JvmDefault' annotation for JVM default method support.
     * {disable|enable|compatibility}
     * */
    override var jvmDefault: String = JvmDefaultMode.DEFAULT.description

    /** Generate metadata with strict version semantics (see kdoc on Metadata.extraInt) */
    override var strictMetadataVersionSemantics: Boolean = false

    /**
     * Transform '(' and ')' in method names to some other character sequence.
     * This mode can BREAK BINARY COMPATIBILITY and is only supposed to be used as a workaround
     * of an issue in the ASM bytecode framework. See KT-29475 for more details
     */
    override var sanitizeParentheses: Boolean = false

    /** Paths to output directories for friend modules (whose internals should be visible) */
    override var friendPaths: List<File> = emptyList()

    /**
     * Path to the JDK to be used
     *
     * If null, no JDK will be used with kotlinc (option -no-jdk)
     * and the system java compiler will be used with empty bootclasspath
     * (on JDK8) or --system none (on JDK9+). This can be useful if all
     * the JDK classes you need are already on the (inherited) classpath.
     * */
    override var jdkHome: File? by default { getJdkHome() }

    /**
     * Path to the kotlin-stdlib.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    override var kotlinStdLibJar: File? by default {
        hostEnvironment.findInHostClasspath( "kotlin-stdlib.jar",
            kotlinDependencyRegex("(kotlin-stdlib|kotlin-runtime)")
        )
    }

    override var kotlinStdLibJdkJar: File? by default {
        hostEnvironment.findInHostClasspath( "kotlin-stdlib-jdk*.jar",
            kotlinDependencyRegex("kotlin-stdlib-jdk[0-9]+")
        )
    }

    /**
     * Path to the kotlin-reflect.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    override var kotlinReflectJar: File? by default {
        hostEnvironment.findInHostClasspath("kotlin-reflect.jar",
            kotlinDependencyRegex("kotlin-reflect")
        )
    }

    /**
     * Path to the kotlin-script-runtime.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    override var kotlinScriptRuntimeJar: File? by default {
        hostEnvironment.findInHostClasspath("kotlin-script-runtime.jar",
            kotlinDependencyRegex("kotlin-script-runtime")
        )
    }

    /**
     * Path to the tools.jar file needed for kapt when using a JDK 8.
     *
     * Note: Using a tools.jar file with a JDK 9 or later leads to an
     * internal compiler error!
     */
    override var toolsJar: File? by default {
        if (!isJdk9OrLater())
            jdkHome?.let { findToolsJarFromJdk(it) }
                ?: hostEnvironment.findInHostClasspath("tools.jar", Regex("tools.jar"))
        else
            null
    }
}