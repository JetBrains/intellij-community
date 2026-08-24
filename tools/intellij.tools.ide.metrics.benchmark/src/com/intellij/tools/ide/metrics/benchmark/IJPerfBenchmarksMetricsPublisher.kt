// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.teamcity.TeamCityClient
import com.intellij.testFramework.BenchmarkTestInfo
import com.intellij.testFramework.UsefulTestCase
import com.intellij.tools.ide.metrics.collector.MetricsCollector
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.publishing.PerformanceMetricsDto
import com.intellij.tools.ide.metrics.collector.telemetry.describeTelemetryJsonFile
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.ide.util.common.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.writer
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Metrics will be stored as TeamCity artifacts and later will be collected by IJ Perf collector (~ once/twice per hour).
 * Charts can be found at [IJ Perf Dashboard](https://ij-perf.labs.jb.gg/intellij/testsDev) - link is prone to change, though.
 */
internal class IJPerfBenchmarksMetricsPublisher {


  companion object {

    fun getIdeTestLogFile(): Path = PathManager.getSystemDir().resolve("testlog").resolve("idea.log")
    fun truncateTestLog(): Unit = run { getIdeTestLogFile().writer(options = arrayOf(StandardOpenOption.TRUNCATE_EXISTING)).write("") }

    // for local testing
    private fun setBuildParams(vararg buildProperties: Pair<String, String>): Path {
      val tempPropertiesFile = FileUtil.createTempFile("teamcity_", "_properties_file.properties")

      Properties().apply {
        setProperty("teamcity.build.id", "225659992")
        setProperty("teamcity.buildType.id", "bt3989238923")
        setProperty("teamcity.agent.jvm.os.name", "Linux")

        buildProperties.forEach { this.setProperty(it.first, it.second) }

        tempPropertiesFile.outputStream().use {
          store(it, "")
        }
      }

      return tempPropertiesFile.toPath()
    }

    private val teamCityClient = TeamCityClient(
      systemPropertiesFilePath =
      // ignoring TC system properties for local test run
      if (UsefulTestCase.IS_UNDER_TEAMCITY) Path(System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE"))
      else setBuildParams()
    )

    @Suppress("TestOnlyProblems")
    private suspend fun prepareMetricsForPublishing(uniqueTestIdentifier: String,
                                                    testClass: Class<*>?,
                                                    vararg metricsCollectors: MetricsCollector): PerformanceMetricsDto {
      delay(1.seconds) // give some time to settle metrics (usually meters) that were published at the end of the test

      val telemetryJsonFile = BenchmarksSpanMetricsCollector.getDefaultPathToTelemetrySpanJson()
      var lastFailure: Throwable? = null
      var lastFileState: String? = null
      var attempt = 0
      val metrics: List<PerformanceMetrics.Metric>? = withRetry("Telemetry metrics should be exported",
                                                               retries = 10, delay = 300.milliseconds) {
        attempt++
        try {
          TelemetryManager.getInstance().forceFlushMetrics()
          metricsCollectors.flatMap {
            it.collect(PathManager.getLogDir())
          }
        }
        catch (e: Throwable) {
          // capture the real cause AND the file state at the moment of failure. withRetry only logs and
          // returns null on exhaustion; and a re-read after the loop races with the writer finishing, so it
          // can misleadingly show a healthy file. Snapshot the accurate read-time state here.
          lastFailure = e
          lastFileState = describeTelemetryJsonFile(telemetryJsonFile)
          logOutput("Perf metrics collection for '$uniqueTestIdentifier' failed on attempt $attempt: $lastFileState")
          throw e
        }
      }

      if (metrics == null) {
        // archive the telemetry file we failed to parse, so the on-disk evidence survives the build (AT-1875);
        // the writer may finalize it between now and the agent picking it up, but the failed-attempt states
        // logged above keep the read-time truth either way
        runCatching {
          teamCityClient.publishTeamCityArtifacts(source = telemetryJsonFile, artifactPath = uniqueTestIdentifier)
        }.onFailure { logOutput("Failed to archive $telemetryJsonFile: ${it.message}") }
        // do not mask: surface the real cause and the failure-time telemetry file state (size/finalized/tail).
        // Per-failed-attempt state is logged above (build log) and distinguishes a write/read race from a
        // never-written file; the exception carries it to the dashboard.
        throw IllegalStateException(
          "Failed to extract perf metrics for '$uniqueTestIdentifier' after $attempt attempts. $lastFileState",
          lastFailure,
        )
      }

      teamCityClient.publishTeamCityArtifacts(source = PathManager.getLogDir(), artifactPath = uniqueTestIdentifier)
      teamCityClient.publishTeamCityArtifacts(source = getIdeTestLogFile(), artifactPath = uniqueTestIdentifier)

      return PerformanceMetricsDto.create(
        projectName = uniqueTestIdentifier,
        projectURL = "",
        projectDescription = "",
        methodName = uniqueTestIdentifier,
        buildNumber = BuildNumber.currentVersion(),
        metrics = metrics,
        owner = testClass?.let { runCatching { codeOwners?.getOwnerGroupName(it) }.getOrNull() } ?: ""
      )
    }

    fun publishSync(fullQualifiedTestMethodName: String, testClass: Class<*>?, vararg metricsCollectors: MetricsCollector) {
      runBlocking {
        publish(fullQualifiedTestMethodName, testClass, *metricsCollectors)
      }
    }

    suspend fun publish(uniqueTestIdentifier: String, testClass: Class<*>?, vararg metricsCollectors: MetricsCollector) {
      val metricsDto = prepareMetricsForPublishing(uniqueTestIdentifier, testClass, *metricsCollectors)

      withContext(Dispatchers.IO) {
        val artifactName = "metrics.performance.json"
        val reportFile = Files.createTempFile("unit-perf-metric", artifactName)
        jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), metricsDto)

        // Print metrics in stdout when running locally
        // https://youtrack.jetbrains.com/issue/AT-644/Performance-tests-do-not-check-anything#focus=Comments-27-8578186.0-0
        // https://youtrack.jetbrains.com/issue/AT-726
        if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
          println("Collected metrics: (can be found in ${teamCityClient.artifactForPublishingDir.resolve(uniqueTestIdentifier).toUri()})")
          println(metricsDto.metrics.joinToString(separator = System.lineSeparator()) { String.format("%-60s %6s", it.n, it.v) })
        }

        teamCityClient.publishTeamCityArtifacts(source = reportFile,
                                                artifactPath = uniqueTestIdentifier,
                                                artifactName = "metrics.performance.json",
                                                zipContent = false)
      }
    }
  }
}
