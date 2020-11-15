package com.tschuchort.compiletesting

import com.tschuchort.compiletesting.OptionName
import com.tschuchort.compiletesting.OptionValue
import java.io.File
import java.nio.file.Path
import javax.annotation.processing.Processor

interface JvmCompilationModel : CompilationModel{
    /** Arbitrary arguments to be passed to kapt */
    var kaptArgs: MutableMap<OptionName, OptionValue>

    /** Annotation processors to be passed to kapt */
    var annotationProcessors: List<Processor>

    /** Include Kotlin runtime in to resulting .jar */
    var includeRuntime: Boolean

    /** Make kapt correct error types */
    var correctErrorTypes: Boolean

    /** Name of the generated .kotlin_module file */
    var moduleName: String?

    /** Target version of the generated JVM bytecode */
    var jvmTarget: String

    /** Generate metadata for Java 1.8 reflection on method parameters */
    var javaParameters: Boolean

    /** Use the IR backend */
    var useIR: Boolean

    /** Paths where to find Java 9+ modules */
    var javaModulePath: Path?

    /**
     * Root modules to resolve in addition to the initial modules,
     * or all modules on the module path if <module> is ALL-MODULE-PATH
     */
    var additionalJavaModules: MutableList<File>

    /** Don't generate not-null assertions for arguments of platform types */
    var noCallAssertions: Boolean

    /** Don't generate not-null assertion for extension receiver arguments of platform types */
    var noReceiverAssertions: Boolean

    /** Don't generate not-null assertions on parameters of methods accessible from Java */
    var noParamAssertions: Boolean

    /** Generate nullability assertions for non-null Java expressions */
    var strictJavaNullabilityAssertions: Boolean

    /** Disable optimizations */
    var noOptimize: Boolean

    /**
     * Normalize constructor calls (disable: don't normalize; enable: normalize),
     * default is 'disable' in language version 1.2 and below, 'enable' since language version 1.3
     *
     * {disable|enable}
     */
    var constructorCallNormalizationMode: String?

    /** Assert calls behaviour {always-enable|always-disable|jvm|legacy} */
    var assertionsMode: String?

    /** Path to the .xml build file to compile */
    var buildFile: File?

    /** Compile multifile classes as a hierarchy of parts and facade */
    var inheritMultifileParts: Boolean

    /** Use type table in metadata serialization */
    var useTypeTable: Boolean

    /** Allow Kotlin runtime libraries of incompatible versions in the classpath */
    var skipRuntimeVersionCheck: Boolean

    /** Path to JSON file to dump Java to Kotlin declaration mappings */
    var declarationsOutputPath: File?

    /** Combine modules for source files and binary dependencies into a single module */
    var singleModule: Boolean

    /** Suppress the \"cannot access built-in declaration\" error (useful with -no-stdlib) */
    var suppressMissingBuiltinsError: Boolean

    /** Script resolver environment in key-value pairs (the value could be quoted and escaped) */
    var scriptResolverEnvironment: MutableMap<String, String>

    /** Java compiler arguments */
    var javacArguments: MutableList<String>

    /** Package prefix for Java files */
    var javaPackagePrefix: String?

    /**
     * Specify behavior for Checker Framework compatqual annotations (NullableDecl/NonNullDecl).
     * Default value is 'enable'
     */
    var supportCompatqualCheckerFrameworkAnnotations: String?

    /** Do not throw NPE on explicit 'equals' call for null receiver of platform boxed primitive type */
    var noExceptionOnExplicitEqualsForBoxedNull: Boolean

    /** Allow to use '@JvmDefault' annotation for JVM default method support.
     * {disable|enable|compatibility}
     * */
    var jvmDefault: String

    /** Generate metadata with strict version semantics (see kdoc on Metadata.extraInt) */
    var strictMetadataVersionSemantics: Boolean

    /**
     * Transform '(' and ')' in method names to some other character sequence.
     * This mode can BREAK BINARY COMPATIBILITY and is only supposed to be used as a workaround
     * of an issue in the ASM bytecode framework. See KT-29475 for more details
     */
    var sanitizeParentheses: Boolean

    /** Paths to output directories for friend modules (whose internals should be visible) */
    var friendPaths: List<File>

    /**
     * Path to the JDK to be used
     *
     * If null, no JDK will be used with kotlinc (option -no-jdk)
     * and the system java compiler will be used with empty bootclasspath
     * (on JDK8) or --system none (on JDK9+). This can be useful if all
     * the JDK classes you need are already on the (inherited) classpath.
     * */
    var jdkHome: File?

    /**
     * Path to the kotlin-stdlib.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    var kotlinStdLibJar: File?

    /**
     * Path to the kotlin-stdlib-jdk*.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    var kotlinStdLibJdkJar: File?

    /**
     * Path to the kotlin-reflect.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    var kotlinReflectJar: File?

    /**
     * Path to the kotlin-script-runtime.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    var kotlinScriptRuntimeJar: File?

    /**
     * Path to the tools.jar file needed for kapt when using a JDK 8.
     *
     * Note: Using a tools.jar file with a JDK 9 or later leads to an
     * internal compiler error!
     */
    var toolsJar: File?

    // *.class files, Jars and resources (non-temporary) that are created by the
// compilation will land here
    val classesDir: File

    // Base directory for kapt stuff
    val kaptBaseDir: File

    // Java annotation processors that are compile by kapt will put their generated files here
    val kaptSourceDir: File

    // Output directory for Kotlin source files generated by kapt
    val kaptKotlinGeneratedDir: File
    val kaptStubsDir: File
    val kaptIncrementalDataDir: File
}