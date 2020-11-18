package com.tschuchort.compiletesting.params

import java.io.File
import java.nio.file.Path

interface JvmCompilationParameters : CompilationParameters {
    /** Include Kotlin runtime in to resulting .jar */
    val includeRuntime: Boolean

    /** Make kapt correct error types */
    val correctErrorTypes: Boolean

    /** Name of the generated .kotlin_module file */
    val moduleName: String?

    /** Target version of the generated JVM bytecode */
    val jvmTarget: String

    /** Generate metadata for Java 1.8 reflection on method parameters */
    val javaParameters: Boolean

    /** Use the IR backend */
    val useIR: Boolean

    /** Paths where to find Java 9+ modules */
    val javaModulePath: Path?

    /**
     * Root modules to resolve in addition to the initial modules,
     * or all modules on the module path if <module> is ALL-MODULE-PATH
     */
    val additionalJavaModules: MutableList<File>

    /** Don't generate not-null assertions for arguments of platform types */
    val noCallAssertions: Boolean

    /** Don't generate not-null assertion for extension receiver arguments of platform types */
    val noReceiverAssertions: Boolean

    /** Don't generate not-null assertions on parameters of methods accessible from Java */
    val noParamAssertions: Boolean

    /** Generate nullability assertions for non-null Java expressions */
    val strictJavaNullabilityAssertions: Boolean

    /** Disable optimizations */
    val noOptimize: Boolean

    /**
     * Normalize constructor calls (disable: don't normalize; enable: normalize),
     * default is 'disable' in language version 1.2 and below, 'enable' since language version 1.3
     *
     * {disable|enable}
     */
    val constructorCallNormalizationMode: String?

    /** Assert calls behaviour {always-enable|always-disable|jvm|legacy} */
    val assertionsMode: String?

    /** Path to the .xml build file to compile */
    val buildFile: File?

    /** Compile multifile classes as a hierarchy of parts and facade */
    val inheritMultifileParts: Boolean

    /** Use type table in metadata serialization */
    val useTypeTable: Boolean

    /** Allow Kotlin runtime libraries of incompatible versions in the classpath */
    val skipRuntimeVersionCheck: Boolean

    /** Path to JSON file to dump Java to Kotlin declaration mappings */
    val declarationsOutputPath: File?

    /** Combine modules for source files and binary dependencies into a single module */
    val singleModule: Boolean

    /** Suppress the \"cannot access built-in declaration\" error (useful with -no-stdlib) */
    val suppressMissingBuiltinsError: Boolean

    /** Script resolver environment in key-value pairs (the value could be quoted and escaped) */
    val scriptResolverEnvironment: MutableMap<String, String>

    /** Java compiler arguments */
    val javacArguments: MutableList<String>

    /** Package prefix for Java files */
    val javaPackagePrefix: String?

    /**
     * Specify behavior for Checker Framework compatqual annotations (NullableDecl/NonNullDecl).
     * Default value is 'enable'
     */
    val supportCompatqualCheckerFrameworkAnnotations: String?

    /** Do not throw NPE on explicit 'equals' call for null receiver of platform boxed primitive type */
    val noExceptionOnExplicitEqualsForBoxedNull: Boolean

    /** Allow to use '@JvmDefault' annotation for JVM default method support.
     * {disable|enable|compatibility}
     * */
    val jvmDefault: String

    /** Generate metadata with strict version semantics (see kdoc on Metadata.extraInt) */
    val strictMetadataVersionSemantics: Boolean

    /**
     * Transform '(' and ')' in method names to some other character sequence.
     * This mode can BREAK BINARY COMPATIBILITY and is only supposed to be used as a workaround
     * of an issue in the ASM bytecode framework. See KT-29475 for more details
     */
    val sanitizeParentheses: Boolean

    /** Paths to output directories for friend modules (whose internals should be visible) */
    val friendPaths: List<File>

    /**
     * Path to the JDK to be used
     *
     * If null, no JDK will be used with kotlinc (option -no-jdk)
     * and the system java compiler will be used with empty bootclasspath
     * (on JDK8) or --system none (on JDK9+). This can be useful if all
     * the JDK classes you need are already on the (inherited) classpath.
     * */
    val jdkHome: File?

    /**
     * Path to the kotlin-stdlib.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    val kotlinStdLibJar: File?

    /**
     * Path to the kotlin-stdlib-jdk*.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    val kotlinStdLibJdkJar: File?

    /**
     * Path to the kotlin-reflect.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    val kotlinReflectJar: File?

    /**
     * Path to the kotlin-script-runtime.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    val kotlinScriptRuntimeJar: File?

    /**
     * Path to the tools.jar file needed for kapt when using a JDK 8.
     *
     * Note: Using a tools.jar file with a JDK 9 or later leads to an
     * internal compiler error!
     */
    val toolsJar: File?

    // *.class files, Jars and resources (non-temporary) that are created by the
    // compilation will land here
    val classesDir get() = workingDir.resolve("classes")
}
