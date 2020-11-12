/**
 * Adds support for KSP (https://goo.gle/ksp).
 */
package com.tschuchort.compiletesting

import com.google.devtools.ksp.AbstractKotlinSymbolProcessingExtension
import com.google.devtools.ksp.KspOptions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.impl.MessageCollectorBasedKSPLogger
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.io.File

/**
 * The list of symbol processors for the kotlin compilation.
 * https://goo.gle/ksp
 */
var KotlinCompilation.symbolProcessors: List<SymbolProcessor>
    get() = getKspRegistrar().processors
    set(value) {
        val registrar = getKspRegistrar()
        registrar.processors = value
    }

/**
 * The directory where generates KSP sources are written
 */
val KotlinCompilation.kspSourcesDir: File
    get() = kspWorkingDir.resolve("sources")

/**
 * Arbitrary arguments to be passed to ksp
 */
var KotlinCompilation.kspArgs: MutableMap<String, String>
    get() = getKspRegistrar().options
    set(value) {
        val registrar = getKspRegistrar()
        registrar.options = value
    }

private val KotlinCompilation.kspJavaSourceDir: File
    get() = kspSourcesDir.resolve("java")

private val KotlinCompilation.kspKotlinSourceDir: File
    get() = kspSourcesDir.resolve("kotlin")

private val KotlinCompilation.kspResourceDir: File
    get() = kspSourcesDir.resolve("resource")

/**
 * The working directory for KSP
 */
private val KotlinCompilation.kspWorkingDir: File
    get() = workingDir.resolve("ksp")

/**
 * The directory where compiled KSP classes are written
 */
// TODO this seems to be ignored by KSP and it is putting classes into regular classes directory
//  but we still need to provide it in the KSP options builder as it is required
//  once it works, we should make the property public.
private val KotlinCompilation.kspClassesDir: File
    get() = kspWorkingDir.resolve("classes")

/**
 * Custom subclass of [AbstractKotlinSymbolProcessingExtension] where processors are pre-defined instead of being
 * loaded via ServiceLocator.
 *
 * Note that this works different from real KSP.
 *
 * The KSP plugin does not participate in main Kotlin compilation since v20201106.
 * Instead, it runs a separate gradle task to run KSP analysis and adds the output of it that task
 * as an input to the main Kotlin compilation.
 *
 * Unfortunately, repeating that behavior is not feasible with the current KSP integration as it
 * would require to run KotlinCompilation twice.
 * Instead, we hack it here to revert it back to the previous behavior to run inside the main
 * compilation by changing the analysis result.
 * see: https://github.com/tschuchortdev/kotlin-compile-testing/issues/72
 * see: https://github.com/google/ksp/commit/328706164554f3036dba08333cdac6d52e8d0ab3
 *
 */
private class KspTestExtension(
    options: KspOptions,
    private val processors: List<SymbolProcessor>,
    logger: KSPLogger
) : AbstractKotlinSymbolProcessingExtension(
    options = options,
    logger = logger,
    testMode = false
) {
    var analysisCompleted = false

    override fun loadProcessors() = processors

    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider
    ): AnalysisResult? {
        if (analysisCompleted) {
            return null
        }
        return super.doAnalysis(project, module, projectContext, files, bindingTrace, componentProvider)
    }

    override fun analysisCompleted(
        project: Project,
        module: ModuleDescriptor,
        bindingTrace: BindingTrace,
        files: Collection<KtFile>
    ): AnalysisResult? {
        if (analysisCompleted) {
            return null
        }
        analysisCompleted = true
        return AnalysisResult.RetryWithAdditionalRoots(
            BindingContext.EMPTY,
            module,
            listOf(options.javaOutputDir),
            listOf(options.kotlinOutputDir),
            addToEnvironment = true
        )
    }
}

/**
 * Registers the [KspTestExtension] to load the given list of processors.
 */
private class KspCompileTestingComponentRegistrar(
    private val compilation: KotlinCompilation
) : ComponentRegistrar {
    var processors = emptyList<SymbolProcessor>()

    var options: MutableMap<String, String> = mutableMapOf()

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        if (processors.isEmpty()) {
            return
        }
        val options = KspOptions.Builder().apply {
            this.processingOptions.putAll(compilation.kspArgs)

            this.classOutputDir = compilation.kspClassesDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
            this.javaOutputDir = compilation.kspJavaSourceDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
            this.kotlinOutputDir = compilation.kspKotlinSourceDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
            this.resourceOutputDir = compilation.kspResourceDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
        }.build()
        // TODO: replace with KotlinCompilation.internalMessageStream
        val registrar = KspTestExtension(
            options, processors, MessageCollectorBasedKSPLogger(
                PrintingMessageCollector(System.err, MessageRenderer.GRADLE_STYLE, compilation.verbose)
            )
        )
        AnalysisHandlerExtension.registerExtension(project, registrar)
    }
}

/**
 * Gets the test registrar from the plugin list or adds if it does not exist.
 */
private fun KotlinCompilation.getKspRegistrar(): KspCompileTestingComponentRegistrar {
    compilerPlugins.firstIsInstanceOrNull<KspCompileTestingComponentRegistrar>()?.let {
        return it
    }
    val kspRegistrar = KspCompileTestingComponentRegistrar(this)
    compilerPlugins = compilerPlugins + kspRegistrar
    return kspRegistrar
}
