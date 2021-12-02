// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.stats

import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig
import org.jetbrains.kotlin.idea.perf.util.Benchmark
import org.jetbrains.kotlin.idea.perf.util.OutputConfig
import org.jetbrains.kotlin.idea.perf.util.loadBenchmark
import org.jetbrains.kotlin.idea.perf.util.statsFile
import java.io.File

internal val statsOutputConfig = OutputConfig(
    writeToJsonFile = false,
    uploadToElasticSearch = false,
    validatePropertyNames = true
)

internal val statsProfilerConfig = ProfilerConfig(dryRun = true)

fun compareBenchmarkWithSample(benchmark: Benchmark): Collection<String>? {
    val statsFileName = benchmark.run {
        val id = buildId
        buildId = null
        val statsFile = statsFile()
        buildId = id

        statsFile.name
    }
    val resource = (Benchmark::class as Any).javaClass.getResource("/stats-samples/$statsFileName") ?: error("no sample for ${statsFileName}")
    val sampleStatFile = File(resource.path)
    val sampleBenchmark = sampleStatFile.loadBenchmark()
    return compare(sampleBenchmark, benchmark).takeIf { it.isNotEmpty() }
}

fun compare(sample: Benchmark, target: Benchmark): Collection<String> {
    val errors = mutableListOf<String>()
    errors.addMissedIf("agentName", sample.agentName, target.agentName)
    errors.addIfNotEquals("benchmark", sample.benchmark, target.benchmark)
    errors.addIfNotEquals("name", sample.name, target.name)
    errors.addIfNotEquals("synthetic", sample.synthetic, target.synthetic)
    errors.addIfNotEquals("index", sample.index, target.index)
    errors.addIfNotEquals("warmUp", sample.warmUp, target.warmUp)
    errors.addMissedIf("buildTimestamp", sample.buildTimestamp, target.buildTimestamp)
    errors.addMissedIf("buildBranch", sample.buildBranch, target.buildBranch)
    errors.addMissedIf("commit", sample.commit, target.commit)
    errors.addMissedIf("buildId", sample.buildId, target.buildId)
    errors.addMissedIf("metricValue", sample.metricValue, target.metricValue)
    errors.addMissedIf("metricError", sample.metricError, target.metricError)
    if (sample.metrics.isNotEmpty()) {
        errors.addMissedIf("metrics", sample.metrics, target.metrics)

        for ((index, sampleMetric) in sample.metrics.withIndex()) {
            val metricName = sampleMetric.metricName
            val targetMetric = target.metrics.firstOrNull { it.metricName == metricName }
            if (targetMetric == null) {
                errors.add("No metric with name metrics['$metricName']")
                continue
            }
            errors.addIfNotEquals("metrics['$metricName'].index", sampleMetric.index, targetMetric.index)
            errors.addIfNotEquals("metrics['$metricName'].warmUp", sampleMetric.warmUp, targetMetric.warmUp)

            errors.addMissedIf("metrics['$metricName'].metricValue", sampleMetric.metricValue, targetMetric.metricValue)
            errors.addMissedIf("metrics['$metricName'].metricError", sampleMetric.metricError, targetMetric.metricError)
            val rawMetrics = sampleMetric.rawMetrics
            if (rawMetrics?.isNotEmpty() == true) {
                errors.addMissedIf("metrics['$metricName'].rawMetrics", rawMetrics, targetMetric.rawMetrics)
                targetMetric.rawMetrics?.let { targetRawMetrics ->
                    for ((rawIndex, sampleRawMetric) in rawMetrics.withIndex()) {
                        val sampleRawMetricName = sampleRawMetric.metricName
                        val targetRawMetric = target.metrics.firstOrNull { it.metricName == metricName }
                        if (targetRawMetric == null) {
                            errors.add("No raw metric with name 'metrics['$metricName'].rawMetrics['$sampleRawMetricName']'")
                            continue
                        }
                        errors.addIfNotEquals(
                            "metrics['$metricName'].rawMetrics['$sampleRawMetricName'].index",
                            sampleMetric.index,
                            targetRawMetric.index
                        )
                        errors.addIfNotEquals(
                            "metrics['$metricName'].rawMetrics['$sampleRawMetricName'].warmUp",
                            sampleMetric.warmUp,
                            targetRawMetric.warmUp
                        )

                        errors.addMissedIf(
                            "metrics['$metricName'].rawMetrics['$sampleRawMetricName'].metricValue",
                            sampleMetric.metricValue,
                            targetRawMetric.metricValue
                        )
                        errors.addMissedIf(
                            "metrics['$metricName'].rawMetrics['$sampleRawMetricName'].metricError",
                            sampleMetric.metricError,
                            targetRawMetric.metricError
                        )
                    }
                }
            }
        }
    }

    return errors
}

private fun MutableCollection<String>.addIfNotEquals(propertyName: String, sampleValue: Any?, targetValue: Any?){
    if (sampleValue != null && sampleValue != targetValue) {
        add("Property '$propertyName' expected: '$sampleValue', actual: '$targetValue'")
    }
}

private fun MutableCollection<String>.addMissedIf(propertyName: String, sampleValue: Any?, targetValue: Any?) {
    if (sampleValue != null && targetValue == null) {
        add("Property '$propertyName' is expected")
    }
}