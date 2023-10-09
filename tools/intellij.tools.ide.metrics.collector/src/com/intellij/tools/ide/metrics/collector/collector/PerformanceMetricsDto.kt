package com.intellij.tools.ide.metrics.collector.collector

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.tools.ide.metrics.collector.publishing.ApplicationMetricDto
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@JsonIgnoreProperties(ignoreUnknown = true)
data class PerformanceMetricsDto(
  val generated: String,
  val project: String,
  val os: String,
  val osFamily: String,
  val runtime: String,
  val build: String,
  val buildDate: String,
  val productCode: String,
  val metrics: List<ApplicationMetricDto>
) {
  companion object {
    fun create(
      projectName: String,
      buildNumber: BuildNumber,
      metrics: Collection<PerformanceMetrics.Metric>
    ) = PerformanceMetricsDto(
      generated = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME),
      project = projectName,
      os = SystemInfo.getOsNameAndVersion(),
      osFamily = SystemInfo.getOsFamily().toString(),
      runtime = SystemInfo.JAVA_VENDOR + " " + SystemInfo.JAVA_VERSION + " " + SystemInfo.JAVA_RUNTIME_VERSION,
      build = buildNumber.asStringWithoutProductCode(),
      buildDate = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME),
      productCode = buildNumber.productCode,
      metrics = metrics.map { it.toJson() }
    )
  }
}
