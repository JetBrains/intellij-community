// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jetbrains.kotlin.idea.testFramework.Stats
import org.jetbrains.kotlin.idea.testFramework.suggestOsNeutralFileName
import org.jetbrains.kotlin.test.KotlinRoot
import java.io.BufferedWriter
import java.io.File

internal fun List<Benchmark>.writeCSV(name: String) {
    val header = listOf("benchmark", "name", "measurement", "value", "buildId", "timestamp")
    fun Benchmark.append(output: BufferedWriter, warmUpValues: Boolean = false) {

        fun values(n: String, value: Long?): String? = value?.let {
            listOf(
                benchmark,
                this.name,
                n,
                value,
                buildId?.toString() ?: "",
                buildTimestamp
            ).joinToString()
        }

        val s = if (warmUpValues) {
            val warmUpValue = metrics.firstOrNull { it.metricName == "_value" }?.let { metric ->
                metric.rawMetrics?.firstOrNull { it.warmUp == true && it.index == 0 }?.metricValue
            }
            values(Stats.WARM_UP + " #0", warmUpValue)
        } else {
            values(Stats.GEOM_MEAN, metricValue)
        }
        s?.let(output::appendLine)
    }

    val statsFile = statsFile(name, "csv")
    statsFile.bufferedWriter().use { output ->
        output.appendLine(header.joinToString())
        for (warmUpValue in arrayOf(true, false)) {
            filterNot { it.name == Stats.GEOM_MEAN }.forEach { it.append(output, warmUpValues = warmUpValue) }
        }
        output.flush()
    }
}

internal fun Metric.writeTeamCityStats(
    name: String,
    rawMeasurementName: String = "rawMetrics",
    rawMetrics: Boolean = false,
    consumer: (String, Long) -> Unit = { propertyName, value ->
        TeamCity.statValue(propertyName, value)
    }
) {
    fun Metric.append(prefix: String, depth: Int) {
        val s = if (this.metricName.isEmpty()) {
            prefix
        } else {
            if (depth == 0 && this.metricName != Stats.GEOM_MEAN) "$prefix: ${this.metricName}" else "$prefix ${this.metricName}"
        }.trim()
        if (s != prefix) {
            metricValue?.let {
                consumer(s, it)
            }
        }
        metrics?.let { list ->
            for (childIndex in list.withIndex()) {
                if (!rawMetrics && childIndex.index > 0) break
                childIndex.value.append(s, depth + 1)
            }
        }
    }

    append(name, 0)
}

internal val kotlinJsonMapper = jacksonObjectMapper()
    .registerKotlinModule()
    .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(SerializationFeature.INDENT_OUTPUT, true)

internal fun Benchmark.statsFile() = statsFile(id(), "json")

internal fun Benchmark.json(): String {
    return kotlinJsonMapper.writeValueAsString(this)
}

internal fun Benchmark.writeJson() {
    val json = json()
    val statsFile = statsFile()
    logMessage { "write $statsFile" }
    statsFile.bufferedWriter().use { output ->
        output.appendLine(json)
        output.flush()
    }
}

internal fun File.loadBenchmark(): Benchmark = kotlinJsonMapper.readValue(this, object : TypeReference<Benchmark>() {})

internal fun Benchmark.loadJson() {
    val statsFile = statsFile()
    if (statsFile.exists()) {
        val value = kotlinJsonMapper.readValue(statsFile, object : TypeReference<Benchmark>() {})
        metrics = value.metrics
    }
}

private fun statsFile(name: String, extension: String) =
    pathToResource("stats${statFilePrefix(name)}.$extension").absoluteFile

internal fun pathToResource(resource: String) = File(KotlinRoot.REPO, "out/$resource").canonicalFile

internal fun statFilePrefix(name: String) = if (name.isNotEmpty()) "-${plainname(name)}" else ""

internal fun plainname(name: String) = suggestOsNeutralFileName(name)
