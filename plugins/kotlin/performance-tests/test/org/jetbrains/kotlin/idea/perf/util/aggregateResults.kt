// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.util

import org.jetbrains.kotlin.test.KotlinRoot
import java.io.File

fun main(args: Array<String>) {
    uploadAggregateResults(File(args[0]))
}

internal fun uploadAggregateResults() {
    val parent = System.getProperty("idea.home.path")?.let(::File) ?: KotlinRoot.REPO
    uploadAggregateResults(File(parent, "out"))
}

private fun uploadAggregateResults(folder: File) {
    val listFiles = folder.listFiles() ?: emptyArray()
    logMessage { "uploadAggregateResults from $folder ${listFiles.joinToString { it.absolutePath }}" }
    val groupBy = listFiles
        .filter { it.length() > 0 && it.name.startsWith("stats-") && it.extension == "json" }
        .groupBy { it.name.replace("stats-", "").split("_")[0] }

    groupBy.forEach { (n, files) ->
        files.map(File::loadBenchmark).writeCSV(n)
    }

    groupBy.forEach { (_, v) ->
        if (v.isEmpty()) return@forEach

        val benchmarks = v.map(File::loadBenchmark)

        benchmarks.forEach { benchmark ->
            benchmark.metrics.firstOrNull { it.metricName == "" }?.let { metric ->
                metric.rawMetrics?.firstOrNull { it.warmUp == true && it.index == 0 }?.let {
                    val warmUpBenchmark = Benchmark(
                        agentName = benchmark.agentName,
                        buildBranch = benchmark.buildBranch,
                        commit = benchmark.commit,
                        buildId = benchmark.buildId,
                        benchmark = benchmark.benchmark,
                        name = benchmark.name,
                        warmUp = it.warmUp,
                        index = it.index,
                        hasError = it.hasError,
                        buildTimestamp = benchmark.buildTimestamp,
                        metricValue = it.metricValue,
                        metricError = it.metricError,
                        metrics = it.metrics ?: emptyList()
                    )
                    warmUpBenchmark.writeJson()
                    ESUploader.upload(warmUpBenchmark)
                }
            }
        }

        // build geom mean benchmark
        val first = v.first()
        val loadBenchmark = first.loadBenchmark()
        val geomMeanBenchmark = Benchmark(
            agentName = loadBenchmark.agentName,
            buildBranch = loadBenchmark.buildBranch,
            commit = loadBenchmark.commit,
            buildId = loadBenchmark.buildId,
            benchmark = loadBenchmark.benchmark,
            synthetic = true,
            name = "geomMean",
            buildTimestamp = loadBenchmark.buildTimestamp
        )

        benchmarks
            .filter { it.synthetic != true && it.warmUp != true }
            .forEach { geomMeanBenchmark.merge(it) }
        geomMeanBenchmark.writeJson()
        ESUploader.upload(geomMeanBenchmark)
    }
}