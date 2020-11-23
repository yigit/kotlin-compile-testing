package com.tschuchort.compiletesting.step

import com.tschuchort.compiletesting.HostEnvironment
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilationUtils
import com.tschuchort.compiletesting.param.CompilationModel

class StepRegistry<Params : CompilationModel> {
    private val steps = LinkedHashMap<String, Entry<Params>>()

    fun registerStep(step: CompilationStep<Params>,
                     shouldRunAfter : Set<String> = emptySet(),
                     shouldRunBefore: Set<String> = emptySet()
    ) {
        if (steps.containsKey(step.id)) {
            throw IllegalArgumentException("step with ${step.id} is already registered")
        }
        if (shouldRunAfter.any { shouldRunBefore.contains(it) }) {
            throw IllegalArgumentException("shouldRunAfter/Before cannot contain the same IDs")
        }
        // we can do final dependency when running steps
        steps[step.id] = Entry(
            step = step,
            shouldRunAfter = shouldRunAfter.toMutableSet(),
            shouldRunBefore = shouldRunBefore.toMutableSet()
        )
    }

    internal fun execute(
        env: HostEnvironment,
        compilationUtils: KotlinCompilationUtils,
        params: Params
    ): ExecutionResult {
        val toBeRun = LinkedHashMap(steps)
        // first move all shouldRunAfter to become dependencies
        toBeRun.forEach { (_, entry) ->
            entry.shouldRunBefore.forEach {
                toBeRun[it]?.shouldRunAfter?.add(entry.step.id)
            }
        }

        var model = params
        var exitCode = KotlinCompilation.ExitCode.OK
        val resultPayloads = mutableListOf<CompilationStep.ResultPayload>()
        while(toBeRun.isNotEmpty()) {
            // find a step w/o any dependencies, execute
            val chosen = toBeRun.values.firstOrNull { step ->
                step.shouldRunAfter.none {
                    toBeRun.contains(it)
                }
            }
            if (chosen == null) {
                throw IllegalStateException("circular dependency detected")
            }
            toBeRun.remove(chosen.step.id)
            val intermediate = chosen.step.execute(
                env = env,
                compilationUtils = compilationUtils,
                model = model
            )
            exitCode = intermediate.exitCode
            model = intermediate.updatedModel
            resultPayloads.add(intermediate.resultPayload)
            if (intermediate.exitCode != KotlinCompilation.ExitCode.OK) {
                break
            }
        }
        return ExecutionResult(
            exitCode = exitCode,
            resultPayloads = resultPayloads
        )
    }

    fun hasStep(id: String): Boolean = steps.containsKey(id)

    private class Entry<Params: CompilationModel>(
        val step: CompilationStep<Params>,
        val shouldRunAfter: MutableSet<String>,
        val shouldRunBefore: MutableSet<String>
    )

    internal class ExecutionResult(
        val exitCode: KotlinCompilation.ExitCode,
        val resultPayloads:List<CompilationStep.ResultPayload>
    )
}