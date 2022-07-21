package com.intellij.metricsCollector.publishing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.metricsCollector.collector.PerformanceMetricsDto
import com.intellij.metricsCollector.metrics.IndexingMetrics
import com.intellij.openapi.util.BuildNumber
import kotlin.io.path.div


const val reportName = "metrics.performance.json"

@Suppress("unused")
fun IndexingMetrics.publishIndexingMetrics(): IndexingMetrics {
  val performanceMetricsJson = this.toPerformanceMetricsJson()
  val jsonWithIndexingMetrics = "indexing.$reportName"
  val jsonReport = this.ideStartResult.context.paths.reportsDir / jsonWithIndexingMetrics
  jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonReport.toFile(), performanceMetricsJson)
  return this
}

fun IndexingMetrics.toPerformanceMetricsJson(): PerformanceMetricsDto {
  val metrics = getListOfIndexingMetrics()
  return PerformanceMetricsDto.create(
    projectName = this.ideStartResult.runContext.contextName,
    buildNumber = BuildNumber.fromStringWithProductCode(ideStartResult.context.ide.build, ideStartResult.context.ide.productCode)!!,
    metrics = metrics
  )
}
