package com.intellij.tools.ide.metrics.collector.publishing

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.metrics.collector.metrics.MetricGroup
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.metrics.toJson
import com.intellij.util.system.OS
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A JSON schema used to report indexing performance metrics to display on https://ij-perf.jetbrains.com [IDEA-251676].
 * The generated .json files will be collected by https://github.com/JetBrains/ij-perf-report-aggregator.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PerformanceMetricsDto(
  val version: String,
  val generated: String,
  val project: String,
  val projectURL: String,
  val projectDescription: String,
  val os: String,
  val osFamily: String,
  val runtime: String,
  val build: String,
  val buildDate: String,
  val branch: String,
  val productCode: String,
  val methodName: String,
  val metrics: List<ApplicationMetricDto>,
  val systemMetrics: Map<String, List<MetricGroup>>,
  val tcInfo: CIServerBuildInfo
) {
  companion object {
    private const val VERSION = "1"

    @JvmStatic
    fun create(
      projectName: String,
      projectURL: String,
      projectDescription: String,
      methodName: String,
      buildNumber: BuildNumber,
      metrics: Collection<PerformanceMetrics.Metric>,
      buildInfo: CIServerBuildInfo,
      generated: String = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)
    ) = PerformanceMetricsDto(
      version = VERSION,
      generated = generated,
      project = projectName,
      projectURL = projectURL,
      os = SystemInfo.getOsNameAndVersion(),
      osFamily = OS.CURRENT.toString(),
      runtime = SystemInfo.JAVA_VENDOR + " " + SystemInfo.JAVA_VERSION + " " + SystemInfo.JAVA_RUNTIME_VERSION,
      build = buildNumber.asStringWithoutProductCode(),
      branch = buildNumber.asStringWithoutProductCode().substringBeforeLast("."),
      // the 'buildDate' field is required for https://ij-perf.jetbrains.com; use any value here
      buildDate = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME),
      productCode = buildNumber.productCode,
      metrics = metrics.map { it.toJson() },
      methodName = methodName,
      systemMetrics = mapOf(),
      tcInfo = buildInfo,
      projectDescription = projectDescription
    )
  }
}
