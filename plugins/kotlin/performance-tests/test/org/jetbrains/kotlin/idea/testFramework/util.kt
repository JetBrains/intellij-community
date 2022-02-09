// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.testFramework

import org.jetbrains.kotlin.idea.perf.profilers.*
import org.jetbrains.kotlin.idea.perf.util.*
import org.jetbrains.kotlin.idea.perf.util.pathToResource
import org.jetbrains.kotlin.idea.perf.util.plainname
import org.jetbrains.kotlin.util.PerformanceCounter
import java.lang.ref.WeakReference
import java.util.HashMap
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals

typealias StatInfos = Map<String, Any>?

// much stricter version of com.intellij.util.PathUtil.suggestFileName(java.lang.String)
// returns OS-neutral file name, which remains the same when generated on different OSes
fun suggestOsNeutralFileName(name: String) = name.asSequence().map { ch ->
    if (ch < 32.toChar() || ch in "<>:\"|?*.\\/;" || ch.isWhitespace()) '_' else ch
}.joinToString(separator = "")


fun <SV, TV> perfTest(
    stats: Stats,
    testName: String,
    warmUpIterations: Int = 5,
    iterations: Int = 20,
    fastIterations: Boolean = false,
    setUp: (TestData<SV, TV>) -> Unit = { },
    test: (TestData<SV, TV>) -> Unit,
    tearDown: (TestData<SV, TV>) -> Unit = { },
    stabilityWatermark: Int? = 20,
    stopAtException: Boolean = false,
) {
    val setUpWrapper = stats.profilerConfig.wrapSetUp(setUp)
    val testWrapper = stats.profilerConfig.wrapTest(test)
    val tearDownWrapper = stats.profilerConfig.wrapTearDown(tearDown)
    val warmPhaseData = PhaseData(
        stats = stats,
        iterations = warmUpIterations,
        testName = testName,
        fastIterations = fastIterations,
        setUp = setUpWrapper,
        test = testWrapper,
        tearDown = tearDownWrapper,
        stabilityWatermark = stabilityWatermark
    )
    val mainPhaseData = PhaseData(
        stats = stats,
        iterations = iterations,
        testName = testName,
        fastIterations = fastIterations,
        setUp = setUpWrapper,
        test = testWrapper,
        tearDown = tearDownWrapper,
        stabilityWatermark = stabilityWatermark
    )
    val block = {
        val metricChildren = mutableListOf<Metric>()
        try {
            warmUpPhase(warmPhaseData, metricChildren, stopAtException)
            val statInfoArray = mainPhase(mainPhaseData, metricChildren, stopAtException)

            if (!mainPhaseData.fastIterations) assertEquals(iterations, statInfoArray.size)

            if (testName != Stats.WARM_UP) {
                // do not estimate stability for warm-up
                if (!testName.contains(Stats.WARM_UP)) {
                    val stabilityPercentage = stats.stabilityPercentage(statInfoArray)
                    logMessage { "$testName stability is $stabilityPercentage %" }
                    val stabilityName = "${stats.name}: $testName stability"

                    val stable = stabilityWatermark?.let { stabilityPercentage <= it } ?: true

                    val error = if (stable) {
                        null
                    } else {
                        "$testName stability is $stabilityPercentage %, above accepted level of $stabilityWatermark %"
                    }

                    TeamCity.test(stabilityName, errorDetails = error, includeStats = false) {
                        metricChildren.add(Metric("stability", metricValue = stabilityPercentage.toLong()))
                    }
                }

                stats.processTimings(testName, statInfoArray, metricChildren)
            } else {
                stats.convertStatInfoIntoMetrics(
                    testName,
                    printOnlyErrors = true,
                    statInfoArray = statInfoArray,
                    metricChildren = metricChildren
                )
            }
        } catch (e: Throwable) {
            stats.processTimings(testName, emptyArray(), metricChildren)
            if (stopAtException) {
                throw e
            }
        }
    }

    if (testName != Stats.WARM_UP) {
        TeamCity.suite(testName, block)
    } else {
        block()
    }

    stats.flush()
}

data class PhaseData<SV, TV>(
    val stats: Stats,
    val iterations: Int,
    val testName: String,
    val setUp: (TestData<SV, TV>) -> Unit,
    val test: (TestData<SV, TV>) -> Unit,
    val tearDown: (TestData<SV, TV>) -> Unit,
    val stabilityWatermark: Int?,
    val fastIterations: Boolean = false
)

data class TestData<SV, TV>(var setUpValue: SV?, var value: TV?) {
    fun reset() {
        setUpValue = null
        value = null
    }
}

private val emptyFun:(TestData<*, *>) -> Unit = {}

private fun <SV, TV> ProfilerConfig.wrapSetUp(setup: (TestData<SV, TV>) -> Unit): (TestData<SV, TV>) -> Unit =
    if (!dryRun) setup else emptyFun

private fun <SV, TV> ProfilerConfig.wrapTest(test: (TestData<SV, TV>) -> Unit): (TestData<SV, TV>) -> Unit =
    if (!dryRun) test else emptyFun

private fun <SV, TV> ProfilerConfig.wrapTearDown(tearDown: (TestData<SV, TV>) -> Unit): (TestData<SV, TV>) -> Unit =
    if (!dryRun) tearDown else emptyFun

private fun <SV, TV> warmUpPhase(
    phaseData: PhaseData<SV, TV>,
    metricChildren: MutableList<Metric>,
    stopAtException: Boolean,
) {
    val warmUpStatInfosArray = phase(phaseData, Stats.WARM_UP, true, stopAtException)

    if (phaseData.testName != Stats.WARM_UP) {
        phaseData.stats.printWarmUpTimings(phaseData.testName, warmUpStatInfosArray, metricChildren)
    } else {
        phaseData.stats.convertStatInfoIntoMetrics(
            phaseData.testName,
            printOnlyErrors = true,
            statInfoArray = warmUpStatInfosArray,
            warmUp = true,
            metricChildren = metricChildren
        ) { attempt -> "warm-up #$attempt" }
    }

    warmUpStatInfosArray.filterNotNull().map { it[Stats.ERROR_KEY] as? Throwable }.firstOrNull()?.let { throw it }
}

private fun <SV, TV> mainPhase(
    phaseData: PhaseData<SV, TV>,
    metricChildren: MutableList<Metric>,
    stopAtException: Boolean,
): Array<StatInfos> {
    val statInfosArray = phase(phaseData, "", stopAtException = stopAtException)
    statInfosArray.filterNotNull().map { it[Stats.ERROR_KEY] as? Throwable }.firstOrNull()?.let {
        phaseData.stats.convertStatInfoIntoMetrics(
            phaseData.testName,
            printOnlyErrors = true,
            statInfoArray = statInfosArray,
            metricChildren = metricChildren
        )
        throw it
    }
    return statInfosArray
}

private fun <SV, TV> phase(
    phaseData: PhaseData<SV, TV>,
    phaseName: String,
    warmup: Boolean = false,
    stopAtException: Boolean,
): Array<StatInfos> {
    val statInfosArray = Array<StatInfos>(phaseData.iterations) { null }
    val testData = TestData<SV, TV>(null, null)

    val stats = phaseData.stats
    try {
        val phaseProfiler =
            createPhaseProfiler(stats.name, phaseData.testName, phaseName, stats.profilerConfig.copy(warmup = warmup))

        for (attempt in 0 until phaseData.iterations) {
            testData.reset()
            triggerGC(attempt)

            val setUpMillis = measureTimeMillis {
                phaseData.setUp(testData)
            }
            val attemptName = "${phaseData.testName} #$attempt"
            //logMessage { "$attemptName setup took $setUpMillis ms" }

            val valueMap = HashMap<String, Any>(2 * PerformanceCounter.numberOfCounters + 1)
            statInfosArray[attempt] = valueMap
            try {
                phaseProfiler.start()
                valueMap[Stats.TEST_KEY] = measureNanoTime {
                    phaseData.test(testData)
                }

                PerformanceCounter.report { name, counter, nanos ->
                    valueMap["counter \"$name\": count"] = counter.toLong()
                    valueMap["counter \"$name\": time"] = nanos.nsToMs
                }

            } catch (t: Throwable) {
                logMessage(t) { "error at $attemptName" }
                valueMap[Stats.ERROR_KEY] = t
                break
            } finally {
                phaseProfiler.stop()
                try {
                    val tearDownMillis = measureTimeMillis {
                        phaseData.tearDown(testData)
                    }
                    //logMessage { "$attemptName tearDown took $tearDownMillis ms" }
                } catch (t: Throwable) {
                    logMessage(t) { "error at tearDown of $attemptName" }
                    valueMap[Stats.ERROR_KEY] = t
                    break
                } finally {
                    PerformanceCounter.resetAllCounters()
                }
            }

            if (phaseData.fastIterations && attempt > 0) {
                val subArray = statInfosArray.take(attempt + 1).toTypedArray()
                val stabilityPercentage = stats.stabilityPercentage(subArray)
                val stable = phaseData.stabilityWatermark?.let { stabilityPercentage <= it } == true
                if (stable) {
                    return subArray
                }
            }
        }
    } catch (t: Throwable) {
        logMessage(t) { "error at ${phaseData.testName}" }
        TeamCity.testFailed(stats.name, error = t)
        if (stopAtException) {
            throw t
        }
    }
    return statInfosArray
}

private fun createPhaseProfiler(
    statsName: String,
    testName: String,
    phaseName: String,
    profilerConfig: ProfilerConfig
): PhaseProfiler {
    profilerConfig.name = "$testName${if (phaseName.isEmpty()) "" else "-$phaseName"}"
    profilerConfig.path = pathToResource("profile/${plainname(statsName)}").path
    val profilerHandler = if (profilerConfig.enabled && !profilerConfig.warmup)
        ProfilerHandler.getInstance(profilerConfig)
    else
        DummyProfilerHandler

    return if (profilerHandler != DummyProfilerHandler) {
        ActualPhaseProfiler(profilerHandler)
    } else {
        DummyPhaseProfiler
    }
}

private fun triggerGC(attempt: Int) {
    if (attempt > 0) {
        val ref = WeakReference(IntArray(32 * 1024))
        while (ref.get() != null) {
            System.gc()
            Thread.sleep(1)
        }
    }
}
