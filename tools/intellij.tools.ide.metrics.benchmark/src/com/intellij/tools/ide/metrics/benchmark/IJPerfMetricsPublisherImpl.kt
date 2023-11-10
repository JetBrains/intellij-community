// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.diagnostic.MetricsPublisher
import com.intellij.teamcity.TeamCityClient
import com.intellij.testFramework.UsefulTestCase
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.publishing.CIServerBuildInfo
import com.intellij.tools.ide.metrics.collector.publishing.PerformanceMetricsDto
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.path.Path

/**
 * Metrics will be stored as TeamCity artifacts and later will be collected by IJ Perf collector (~ once/twice per hour).
 * Charts can be found at [IJ Perf Dashboard](https://ij-perf.labs.jb.gg/intellij/testsDev) - link is prone to change, though.
 */
class IJPerfMetricsPublisherImpl : MetricsPublisher {
  companion object {
    // for local testing
    private fun setBuildParams(vararg buildProperties: Pair<String, String>): Path {
      val tempPropertiesFile = FileUtil.createTempFile("teamcity_", "_properties_file.properties")

      Properties().apply {
        setProperty("teamcity.build.id", "225659992")
        setProperty("teamcity.buildType.id", "bt3989238923")
        setProperty("teamcity.agent.jvm.os.name", "Linux")

        buildProperties.forEach { this.setProperty(it.first, it.second) }

        store(tempPropertiesFile.outputStream(), "")
      }

      return tempPropertiesFile.toPath()
    }

    private val teamCityClient = TeamCityClient(
      systemPropertiesFilePath =
      // ignoring TC system properties for local test run
      if (UsefulTestCase.IS_UNDER_TEAMCITY) Path(System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE"))
      else setBuildParams()
    )

    private fun prepareMetricsForPublishing(fullQualifiedTestMethodName: String, spanName: String): PerformanceMetricsDto {
      val metrics: List<PerformanceMetrics.Metric> = MetricsExtractor(PathManager.getLogDir().resolve("opentelemetry.json").toFile())
        .waitTillMetricsExported(spanName)

      teamCityClient.publishTeamCityArtifacts(source = PathManager.getLogDir(), artifactPath = fullQualifiedTestMethodName)

      val buildInfo = CIServerBuildInfo(
        buildId = teamCityClient.buildId,
        typeId = teamCityClient.buildTypeId,
        configName = teamCityClient.configurationName ?: "",
        buildNumber = teamCityClient.buildNumber,
        branchName = teamCityClient.branchName,
        url = String.format("%s/viewLog.html?buildId=%s&buildTypeId=%s", teamCityClient.baseUri,
                            teamCityClient.buildId,
                            teamCityClient.buildTypeId),
        isPersonal = teamCityClient.isPersonalBuild,
        timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
      )

      return PerformanceMetricsDto.create(
        projectName = fullQualifiedTestMethodName,
        projectURL = "",
        projectDescription = "",
        methodName = fullQualifiedTestMethodName,
        buildNumber = ApplicationInfo.getInstance().build,
        metrics = metrics,
        buildInfo = buildInfo
      )
    }
  }

  override fun publish(fullQualifiedTestMethodName: String, metricName: String) {
    val metricsDto = prepareMetricsForPublishing(fullQualifiedTestMethodName, metricName)

    val artifactName = "metrics.performance.json"
    val reportFile = Files.createTempFile("unit-perf-metric", artifactName)
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), metricsDto)
    teamCityClient.publishTeamCityArtifacts(source = reportFile,
                                            artifactPath = fullQualifiedTestMethodName,
                                            artifactName = "metrics.performance.json",
                                            zipContent = false)
  }
}