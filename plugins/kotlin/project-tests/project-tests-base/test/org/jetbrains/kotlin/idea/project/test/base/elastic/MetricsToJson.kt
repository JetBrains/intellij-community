// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.elastic

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.project.test.base.ProjectData
import org.jetbrains.kotlin.idea.project.test.base.actions.ProjectAction
import org.jetbrains.kotlin.idea.project.test.base.metrics.Metric
import org.jetbrains.kotlin.idea.project.test.base.metrics.MetricsData
import org.jetbrains.kotlin.idea.testFramework.Stats

object MetricsToJson {
    fun toJsonString(action: ProjectAction, project: ProjectData, frontend: KotlinPluginKind, iterations: List<MetricsData>): String {
        val mapper = ObjectMapper()
        val rootNode = mapper.createObjectNode().apply {
            put("buildBranch", Stats.BENCHMARK_STUB.buildBranch ?: "NO_BRANCH")
            put("buildId", Stats.BENCHMARK_STUB.buildId?.toLong() ?: System.currentTimeMillis())
            put("frontend", frontend.frontendId)
            put("project", project.id)
            put("file", action.filePath)
            put("action", action.id)
            put("iterations",
                mapper.createArrayNode().apply {
                    iterations.forEach { metricsData ->
                        add(addIteration(mapper, metricsData))
                    }
                }
            )
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode)
    }

    private fun addIteration(
        mapper: ObjectMapper,
        metricsData: MetricsData
    ): ObjectNode {
        return mapper.createObjectNode().apply {
            put("iteration", metricsData.iteration)
            put("metrics",
                mapper.createArrayNode().apply {
                    metricsData.metrics.forEach { (metric, value) ->
                        add(metric(mapper, metric, value))
                    }
                }
            )
        }
    }

    private fun metric(
        mapper: ObjectMapper,
        metric: Metric,
        value: Long
    ): ObjectNode {
        return mapper.createObjectNode().apply {
            put("metric", metric.id)
            put("value", value)
        }
    }

    private val KotlinPluginKind.frontendId: String
        get() = when (this) {
            KotlinPluginKind.FE10_PLUGIN -> "FE10"
            KotlinPluginKind.FIR_PLUGIN -> "FIR"
        }

}