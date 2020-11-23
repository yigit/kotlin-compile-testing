/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tschuchort.compiletesting

import com.facebook.buck.jvm.java.javax.com.tschuchort.compiletesting.step.KotlinJvmCompilationStep
import com.tschuchort.compiletesting.param.JvmCompilationModel
import com.tschuchort.compiletesting.param.JvmCompilationModelImpl
import com.tschuchort.compiletesting.step.CompilationStep
import com.tschuchort.compiletesting.step.JavacCompilationStep
import com.tschuchort.compiletesting.step.StepRegistry
import java.io.*
import java.net.URLClassLoader
import java.nio.file.Path

data class PluginOption(val pluginId: PluginId, val optionName: OptionName, val optionValue: OptionValue)

typealias PluginId = String
typealias OptionName = String
typealias OptionValue = String

@Suppress("MemberVisibilityCanBePrivate")
class KotlinCompilation internal constructor(
	private val model:JvmCompilationModelImpl
): AbstractKotlinCompilation<JvmCompilationModel>(model) {

	constructor(): this(JvmCompilationModelImpl())

	/** Include Kotlin runtime in to resulting .jar */
	var includeRuntime: Boolean by model::includeRuntime

	/** Make kapt correct error types */
	var correctErrorTypes: Boolean by model::correctErrorTypes

	/** Name of the generated .kotlin_module file */
	var moduleName: String? by model::moduleName

	/** Target version of the generated JVM bytecode */
	var jvmTarget: String by model::jvmTarget

	/** Generate metadata for Java 1.8 reflection on method parameters */
	var javaParameters: Boolean by model::javaParameters

	/** Use the IR backend */
	var useIR: Boolean by model::useIR

	/** Paths where to find Java 9+ modules */
	var javaModulePath: Path? by model::javaModulePath

	/**
	 * Root modules to resolve in addition to the initial modules,
	 * or all modules on the module path if <module> is ALL-MODULE-PATH
	 */
	var additionalJavaModules: MutableList<File> by model::additionalJavaModules

	/** Don't generate not-null assertions for arguments of platform types */
	var noCallAssertions: Boolean by model::noCallAssertions

	/** Don't generate not-null assertion for extension receiver arguments of platform types */
	var noReceiverAssertions: Boolean by model::noReceiverAssertions

	/** Don't generate not-null assertions on parameters of methods accessible from Java */
	var noParamAssertions: Boolean by model::noParamAssertions

	/** Generate nullability assertions for non-null Java expressions */
	var strictJavaNullabilityAssertions: Boolean by model::strictJavaNullabilityAssertions

	/** Disable optimizations */
	var noOptimize: Boolean by model::noOptimize

	/**
	 * Normalize constructor calls (disable: don't normalize; enable: normalize),
	 * default is 'disable' in language version 1.2 and below, 'enable' since language version 1.3
	 *
	 * {disable|enable}
	 */
	var constructorCallNormalizationMode: String? by model::constructorCallNormalizationMode

	/** Assert calls behaviour {always-enable|always-disable|jvm|legacy} */
	var assertionsMode: String? by model::assertionsMode

	/** Path to the .xml build file to compile */
	var buildFile: File? by model::buildFile

	/** Compile multifile classes as a hierarchy of parts and facade */
	var inheritMultifileParts: Boolean by model::inheritMultifileParts

	/** Use type table in metadata serialization */
	var useTypeTable: Boolean by model::useTypeTable

	/** Allow Kotlin runtime libraries of incompatible versions in the classpath */
	var skipRuntimeVersionCheck: Boolean by model::skipRuntimeVersionCheck

	/** Path to JSON file to dump Java to Kotlin declaration mappings */
	var declarationsOutputPath: File? by model::declarationsOutputPath

	/** Combine modules for source files and binary dependencies into a single module */
	var singleModule: Boolean by model::singleModule

	/** Suppress the \"cannot access built-in declaration\" error (useful with -no-stdlib) */
	var suppressMissingBuiltinsError: Boolean by model::suppressMissingBuiltinsError

	/** Script resolver environment in key-value pairs (the value could be quoted and escaped) */
	var scriptResolverEnvironment: MutableMap<String, String> by model::scriptResolverEnvironment

	/** Java compiler arguments */
	var javacArguments: MutableList<String> by model::javacArguments

	/** Package prefix for Java files */
	var javaPackagePrefix: String? by model::javaPackagePrefix

	/**
	 * Specify behavior for Checker Framework compatqual annotations (NullableDecl/NonNullDecl).
	 * Default value is 'enable'
	 */
	var supportCompatqualCheckerFrameworkAnnotations: String? by model::supportCompatqualCheckerFrameworkAnnotations

	/** Do not throw NPE on explicit 'equals' call for null receiver of platform boxed primitive type */
	var noExceptionOnExplicitEqualsForBoxedNull: Boolean by model::noExceptionOnExplicitEqualsForBoxedNull

	/** Allow to use '@JvmDefault' annotation for JVM default method support.
	 * {disable|enable|compatibility}
	 * */
	var jvmDefault: String by model::jvmDefault

	/** Generate metadata with strict version semantics (see kdoc on Metadata.extraInt) */
	var strictMetadataVersionSemantics: Boolean by model::strictMetadataVersionSemantics

	/**
	 * Transform '(' and ')' in method names to some other character sequence.
	 * This mode can BREAK BINARY COMPATIBILITY and is only supposed to be used as a workaround
	 * of an issue in the ASM bytecode framework. See KT-29475 for more details
	 */
	var sanitizeParentheses: Boolean by model::sanitizeParentheses

	/** Paths to output directories for friend modules (whose internals should be visible) */
	var friendPaths: List<File> by model::friendPaths

	/**
	 * Path to the JDK to be used
	 *
	 * If null, no JDK will be used with kotlinc (option -no-jdk)
	 * and the system java compiler will be used with empty bootclasspath
	 * (on JDK8) or --system none (on JDK9+). This can be useful if all
	 * the JDK classes you need are already on the (inherited) classpath.
	 * */
	var jdkHome: File? by model::jdkHome

	/**
	 * Path to the kotlin-stdlib.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	var kotlinStdLibJar: File? by model::kotlinStdLibJar

	/**
	 * Path to the kotlin-stdlib-jdk*.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	var kotlinStdLibJdkJar: File? by model::kotlinStdLibJdkJar

	/**
	 * Path to the kotlin-reflect.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	var kotlinReflectJar: File? by model::kotlinReflectJar

	/**
	 * Path to the kotlin-script-runtime.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	var kotlinScriptRuntimeJar: File? by model::kotlinScriptRuntimeJar

	/**
	 * Path to the tools.jar file needed for kapt when using a JDK 8.
	 *
	 * Note: Using a tools.jar file with a JDK 9 or later leads to an
	 * internal compiler error!
	 */
	var toolsJar: File? by model::toolsJar

	// *.class files, Jars and resources (non-temporary) that are created by the
	// compilation will land here
	// TODO remove or move into Result?
	val classesDir get() = workingDir.resolve("classes")

	/** ExitCode of the entire Kotlin compilation process */
	enum class ExitCode {
		OK, INTERNAL_ERROR, COMPILATION_ERROR, SCRIPT_EXECUTION_ERROR
	}

	/** Result of the compilation */
	class Result(
		/** The exit code of the compilation */
		val exitCode: ExitCode,
		/** Messages that were printed by the compilation */
		val messages: String,
		/** The directory where only the final output class and resources files will be */
		val outputDirectory: File,
		/**
		 * 	Intermediate source and resource files generated by the annotation processor that
		 * 	will be compiled in the next steps.
		 */
		val sourcesGeneratedByAnnotationProcessor: List<File>
	) {
		/** class loader to load the compile classes */
		val classLoader = URLClassLoader(arrayOf(outputDirectory.toURI().toURL()),
			this::class.java.classLoader)


				//kaptSourceDir.listFilesRecursively() + kaptKotlinGeneratedDir.listFilesRecursively()

		/**
		 * Compiled class and resource files that are the final result of the compilation.
		 */
		val compiledClassAndResourceFiles: List<File> = outputDirectory.listFilesRecursively()

		/** Stub files generated by kapt */
		val generatedStubFiles: List<File> = emptyList()//TODO("get from kapt model?")//kaptStubsDir.listFilesRecursively()

		/**
		 * The class, resource and intermediate source files generated during the compilation.
		 * Does not include stub files and kapt incremental data.
		 */
		val generatedFiles: Collection<File>
				= sourcesGeneratedByAnnotationProcessor + compiledClassAndResourceFiles + generatedStubFiles
	}

	/** Runs the compilation task */
	fun compile(): Result {
		// add java and kotlin compilation if they are not added
		if (!hasStep(KotlinJvmCompilationStep.ID)) {
			registerStep(
				step = KotlinJvmCompilationStep()
			)
		}
		if (!hasStep(JavacCompilationStep.ID)) {
			registerStep(
				step = JavacCompilationStep(),
				shouldRunAfter = setOf(KotlinJvmCompilationStep.ID)
			)
		}

		pluginClasspaths.forEach { filepath ->
			if (!filepath.exists()) {
				model.messageStream.error("Plugin $filepath not found")
				return makeInternalErrorResult()
			}
		}

		/* Work around for warning that sometimes happens:
		"Failed to initialize native filesystem for Windows
		java.lang.RuntimeException: Could not find installation home path.
		Please make sure bin/idea.properties is present in the installation directory"
		See: https://github.com/arturbosch/detekt/issues/630
		*/
		val exeuctionResult = withSystemProperty("idea.use.native.fs.for.win", "false") {
			runSteps()
		}
		return makeResult(exeuctionResult)
	}

	private fun makeInternalErrorResult(): Result {
		val messages = model.messageStream.collectLog()

		searchSystemOutForKnownErrors(messages)
		// figure out how to collect output directories
		return Result(
			exitCode = ExitCode.INTERNAL_ERROR,
			messages = messages,
			outputDirectory = workingDir.resolve("error-placeholder-dir").also { it.mkdirs() },
			sourcesGeneratedByAnnotationProcessor = emptyList()
		)
	}

	private fun makeResult(
		executionResult: StepRegistry.ExecutionResult
	): Result {
		val messages = model.messageStream.collectLog()

		if(executionResult.exitCode != ExitCode.OK)
			searchSystemOutForKnownErrors(messages)

		val classesOutput = workingDir.resolve("final-output-classes").also {
			it.deleteRecursively()
			it.mkdirs()
		}
		val generatedSourcesOutput = workingDir.resolve("final-output-sources").also {
			it.deleteRecursively()
			it.mkdirs()
		}
		// copy all outputs except for source files
		executionResult.outputFolders.forEach { folder ->
			// TODO filter src files out
			folder.copyRecursively(classesOutput)
		}
		executionResult.outputFolders.forEach { folder ->
			// TODO filter source files
			folder.copyRecursively(generatedSourcesOutput)
		}

		// figure out how to collect output directories
		return Result(
			exitCode = executionResult.exitCode,
			messages = messages,
			outputDirectory = classesOutput,
			sourcesGeneratedByAnnotationProcessor = generatedSourcesOutput.listFilesRecursively())
	}

	companion object {
		const val OPTION_KAPT_KOTLIN_GENERATED = "kapt.kotlin.generated"
    }
}
