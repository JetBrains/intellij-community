// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.util


import com.fasterxml.jackson.core.type.TypeReference
import java.io.File
import java.io.FilenameFilter
import kotlin.system.exitProcess

/**
 * This is a small handy tool allowing to evaluate subsystem performance (improvement/degradation) after some change.
 *
 * Prerequisites:
 * - subsystem needs be supplied with performance tests providing statistics as a set of JSON files each representing
 * serialized form of [Benchmark].
 * - required performance tests need to be launched against state-before and state-after
 *
 * Example:
 * 1. Launch tests derived from [org.jetbrains.kotlin.idea.perf.AbstractPerformanceCompletionHandlerTests] for two commits: before and after
 * (don't forget to copy results into a separate directory to avoid overriding, e.g. '/home/user/kotlin-ide/BEFORE' and
 * '/home/user/kotlin-ide/AFTER')
 * 2. Set [pathBefore] = "/home/user/kotlin-ide/BEFORE" and [pathAfter] = "/home/user/kotlin-ide/AFTER". When needed update the value
 * of [printDegradedOnly] and [topN].
 * 3. Launch [main]
 * 4. Output will contain a list of tests with degraded performance sorted by 'DIFF [%]' is descending order.
 * ```
 * TOP-5
 * BEFORE [ms], AFTER [ms], DIFF [ms], DIFF [%]
 * 5     6       1    20.0%     completion-basic-charFilter.KeywordAndSpace
 * 13    15      2    15.4%     completion-basic.KT36306
 * 8     9       1    12.5%     completion-smart.AutoCompleteAfterAs3
 * 8     9       1    12.5%     completion-smart.AutoCompleteAfterAs2
 * 9     10      1    11.1%     completion-basic.ImportAliases - KDoc
 *
 * ```
 */

private const val pathBefore = "/home/klunnyi/jb/kotlin-ide/performance-tests_BEFORE"
private const val pathAfter = "/home/klunnyi/jb/kotlin-ide/performance-tests_AFTER"
private const val printDegradedOnly = true
private const val topN = 5

private data class PerformanceComparison(
    val benchmark: String, val valueBefore: Long, val valueAfter: Long,
    val diff: Long = valueAfter - valueBefore,
    val diffPercent: Double = (valueAfter.toDouble() / valueBefore - 1) * 100
)

fun main() {
    println("Stats before: ${pathBefore}")
    println("Stats after: ${pathAfter}")

    var comparisonRecords = collectComparisonRecords(File(pathBefore), File(pathAfter))
    if (comparisonRecords == null) {
        print("Please, make sure that provided directories exist and contain valid benchmark files")
        exitProcess(1)
    }

    println("Benchmark files compared: ${comparisonRecords.size}")

    if (printDegradedOnly) {
        comparisonRecords = comparisonRecords.filter { it.diff > 0 }
        println("Degraded benchmarks detected: ${comparisonRecords.size}")
    }

    comparisonRecords = comparisonRecords.sortedByDescending { it.diffPercent }.take(topN)

    println("\nTOP-${topN}")
    println("BEFORE [ms], AFTER [ms], DIFF [ms], DIFF [%]")
    comparisonRecords.forEach {
        println("%-5d %-5d %3d %7.1f%% %3s %-120s".format(it.valueBefore, it.valueAfter, it.diff, it.diffPercent, "", it.benchmark))
    }
}

private fun collectComparisonRecords(beforeDir: File, afterDir: File): List<PerformanceComparison>? {
    val result: MutableList<PerformanceComparison> = mutableListOf()
    val filenameFilter = FilenameFilter { _, name -> name.endsWith(".json") }

    val filesBefore = beforeDir.listFiles(filenameFilter) ?: return null
    filesBefore.forEach { fileBefore ->
        val fileAfter = File(afterDir, fileBefore.name).takeIf { it.exists() } ?: return@forEach
        val (name, valueBefore) = fileBefore.extractMetric("geomMean") ?: return@forEach
        val (_, valueAfter) = fileAfter.extractMetric("geomMean") ?: return@forEach
        result.add(PerformanceComparison(name, valueBefore, valueAfter))
    }

    return result
}

private fun File.extractMetric(metricName: String): Pair<String, Long>? {
    val benchmark = kotlinJsonMapper.readValue(this, object : TypeReference<Benchmark>() {}) ?: return null
    val metricValue = benchmark.metrics.find { it.metricName == metricName }?.metricValue ?: return null
    return ("${benchmark.benchmark}.${benchmark.name}") to metricValue
}