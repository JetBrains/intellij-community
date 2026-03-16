package com.intellij.tools.ide.metrics.collector.starter.publishing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.metrics.CommonMetrics
import com.intellij.tools.ide.metrics.collector.starter.metrics.GCLogAnalyzer
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Files
import java.nio.file.Path

class DefaultMetricsPublisher : MetricsPublisher<DefaultMetricsPublisher>() {
  override val publishAction: (IDEStartResult, List<PerformanceMetrics.Metric>) -> Unit = { ideStartResult, metrics ->
    val reportFile: Path = Files.createTempFile("metrics", ".json")

    val metricsSortedByName =
      (metrics
       + CommonMetrics.getJvmMetrics(ideStartResult)
       + CommonMetrics.getAwtMetrics(ideStartResult)
       + GCLogAnalyzer(ideStartResult).getGCMetrics()
       + CommonMetrics.getWriteActionMetrics(ideStartResult)
      ).sortedBy { it.id.name }

    logOutput("Default metrics publisher is registered. If you need to publish your metrics - register your own publisher via DI.")

    logOutput("All collected metrics: " + metricsSortedByName.joinToString(separator = System.lineSeparator()) {
      "${it.id.name} ${it.value}"
    })

    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), metricsSortedByName)

    // You can implement your own logic for CIServer and register it via Kodein DI
    // more about that you can find in Starter readme
    ideStartResult.context.publishArtifact(source = reportFile, artifactName = "metrics.performance.json")
  }
}