package com.tschuchort.compiletesting

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

@ExperimentalTime
class TimeTracking : TestRule {
    private val recordings = mutableMapOf<Pair<String, ProfilingTest.TestConfiguration>, MutableList<Long>>()
    fun record(
        description: Description,
        testConfiguration: ProfilingTest.TestConfiguration,
        nanos: Long
    ) {
        val testName = description.methodName.substring(
            0, description.methodName.indexOf('[')
        )
        val key = testName to testConfiguration
        recordings.getOrPut(key) {
            mutableListOf()
        }.add(nanos)
    }
    fun printReport() {
        System.err.println("measurements:")
        val analyzed = recordings.map { entry ->
            entry.key to analyze(entry.value.removeOutliers())
        }
        analyzed.sortedBy {
            it.second.median
        }.forEach {
            System.err.println("{${it.second.median}: ${it.first}\n ${it.second}\n")
        }
        val groupedByTestName = recordings.entries.groupBy {
            it.key.first
        }
        groupedByTestName.forEach { (testName, measurements) ->
            printPerTestReport(
                testName,
                measurements.groupBy { it.key.second }.mapValues {
                    it.value.flatMap { it.value }
                }
            )
        }
    }

    fun printPerTestReport(
        testName:String,
        measurements: Map<ProfilingTest.TestConfiguration, List<Long>>
    ) {
        System.err.println("report for $testName")
        measurements.mapValues {
            analyze(it.value.removeOutliers())
        }.entries.sortedBy {
            it.value.avg
        }.forEach {
            System.err.println("${it.key} (${it.value.avg}) : ${it.value}")
        }
    }

    fun analyze(
        measurements : List<Long>
    ): AnalysisResult {
        return AnalysisResult(
            avg = measurements.average().roundToLong().toDuration(DurationUnit.NANOSECONDS),
            median = measurements.median().toDuration(DurationUnit.NANOSECONDS),
            sampleSize = measurements.size,
            min = measurements.minOrNull()!!.toDuration(DurationUnit.NANOSECONDS),
            max = measurements.maxOrNull()!!.toDuration(DurationUnit.NANOSECONDS)
        )
    }

    private fun List<Long>.removeOutliers(): List<Long> {
        // simply remove top 10% and bottom 10% :D
        if (size < 10) return this
        val sorted = sorted()
        val removeCnt = size / 10
        return sorted.drop(removeCnt).dropLast(removeCnt)
    }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    warmUp()
                    base.evaluate()
                } finally {
                    printReport()
                }
            }
        }
    }

    private fun warmUp() {
        // run some compilations
        repeat(2) {
            KotlinCompilation().apply {
                sources = listOf(SourceFile.kotlin("Foo.kt", ""))
                annotationProcessors = listOf(ProfilingTest.DoNothingKapt())
                symbolProcessors = listOf(ProfilingTest.DonothingKsp())
            }.compile()
        }
        repeat(2) {
            KotlinCompilation().apply {
                sources = listOf(SourceFile.kotlin("Foo.kt", ""))
                annotationProcessors = listOf(ProfilingTest.DoNothingKapt())
                symbolProcessors = listOf(ProfilingTest.DonothingKsp())
            }.compile()
        }
    }

    private fun List<Long>.median() : Long {
        if (isEmpty()) return -1
        val mid = size / 2
        return if (size % 2 == 0) {
            ((this[mid] + this[mid - 1]).toDouble() / 2).roundToLong()
        } else {
            this[mid]
        }
    }

    data class AnalysisResult(
        val avg: Duration,
        val median: Duration,
        val sampleSize: Int,
        val min:Duration,
        val max:Duration
    )
}