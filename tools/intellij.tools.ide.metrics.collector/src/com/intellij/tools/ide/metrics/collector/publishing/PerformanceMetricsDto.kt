package com.intellij.tools.ide.metrics.collector.publishing

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.openapi.util.BuildNumber
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.metrics.toJson
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


typealias PerformanceMetricsDto = IJPerfMetricsDto<Int>
/**
 * A JSON schema used to report indexing performance metrics to display on https://ij-perf.jetbrains.com .
 * The generated .json files will be collected by https://github.com/JetBrains/ij-perf-report-aggregator.
 * See: https://github.com/JetBrains/ij-perf-report-aggregator/blob/master/schemas/performanceMetrics.schema.json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class IJPerfMetricsDto<T: Number>(
  val version: String,
  val generated: String,
  val project: String,
  val projectURL: String,
  val projectDescription: String,
  val build: String,
  val buildDate: String,
  val methodName: String,
  val mode: String = "",
  val metrics: List<ApplicationMetricDto<T>>,
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
      generated: String = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME),
      mode: String = "",
    ) = create(projectName, projectURL, projectDescription, methodName, buildNumber, metrics.map { it.toJson() }, generated, mode)

    @JvmStatic
    fun <T: Number> create(
      projectName: String,
      projectURL: String,
      projectDescription: String,
      methodName: String,
      buildNumber: BuildNumber,
      metrics: List<ApplicationMetricDto<T>>,
      generated: String = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME),
      mode: String = "",
    ) = IJPerfMetricsDto(
      version = VERSION,
      generated = generated,
      project = projectName,
      projectURL = projectURL,
      build = buildNumber.asStringWithoutProductCode(),
      // the 'buildDate' field is required for https://ij-perf.jetbrains.com; use any value here
      buildDate = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME),
      metrics = metrics,
      methodName = methodName,
      projectDescription = projectDescription,
      mode = mode
    )
  }
}
