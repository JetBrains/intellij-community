package com.intellij.metricsCollector.metrics

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.metricsCollector.collector.PerformanceMetrics
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.dto.JsonIndexDiagnostic
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.streams.toList

val metricIndexing = PerformanceMetrics.MetricId.Duration("indexing")
val metricScanning = PerformanceMetrics.MetricId.Duration("scanning")
val metricUpdatingTime = PerformanceMetrics.MetricId.Duration("updatingTime")
val metricNumberOfIndexedFiles = PerformanceMetrics.MetricId.Counter("numberOfIndexedFiles")
val metricNumberOfFilesIndexedByExtensions = PerformanceMetrics.MetricId.Counter("numberOfFilesIndexedByExtensions")
val metricNumberOfIndexingRuns = PerformanceMetrics.MetricId.Counter("numberOfIndexingRuns")
val metricIds = listOf(metricIndexing, metricScanning, metricNumberOfIndexedFiles, metricNumberOfFilesIndexedByExtensions,
                       metricNumberOfIndexingRuns)


data class IndexingMetrics(
  val ideStartResult: IDEStartResult,
  val jsonIndexDiagnostics: List<JsonIndexDiagnostic>
) {

  val totalNumberOfIndexingRuns: Int
    get() = jsonIndexDiagnostics.count { it.projectIndexingHistory.projectName.isNotEmpty() }

  private val totalUpdatingTime: Long
    get() = jsonIndexDiagnostics.map { TimeUnit.NANOSECONDS.toMillis(it.projectIndexingHistory.times.totalUpdatingTime.nano) }.sum()

  val totalIndexingTime: Long
    get() = jsonIndexDiagnostics.map { TimeUnit.NANOSECONDS.toMillis(it.projectIndexingHistory.times.indexingTime.nano) }.sum()

  val totalScanFilesTime: Long
    get() = jsonIndexDiagnostics.map { TimeUnit.NANOSECONDS.toMillis(it.projectIndexingHistory.times.scanFilesTime.nano) }.sum()

  @Suppress("unused")
  val totalPushPropertiesTime: Long
    get() = jsonIndexDiagnostics.map { TimeUnit.NANOSECONDS.toMillis(it.projectIndexingHistory.times.pushPropertiesTime.nano) }.sum()

  private val suspendedTime: Long
    get() = jsonIndexDiagnostics.map { TimeUnit.NANOSECONDS.toMillis(it.projectIndexingHistory.times.totalSuspendedTime.nano) }.sum()

  val totalNumberOfIndexedFiles: Int
    get() = jsonIndexDiagnostics.sumOf { diagnostic ->
      diagnostic.projectIndexingHistory.fileProviderStatistics.sumOf { it.totalNumberOfIndexedFiles }
    }

  val totalNumberOfScannedFiles: Int
    get() = jsonIndexDiagnostics.sumOf { diagnostic ->
      diagnostic.projectIndexingHistory.scanningStatistics.sumOf { it.numberOfScannedFiles }
    }

  val totalNumberOfFilesFullyIndexedByExtensions: Int
    get() = jsonIndexDiagnostics.map { it.projectIndexingHistory.fileProviderStatistics.map { provider -> provider.totalNumberOfFilesFullyIndexedByExtensions }.sum() }.sum() +
            jsonIndexDiagnostics.map { it.projectIndexingHistory.scanningStatistics.map { scan -> scan.numberOfFilesFullyIndexedByInfrastructureExtensions }.sum() }.sum()

  val listOfFilesFullyIndexedByExtensions: List<String>
    get() {
      val indexedFiles = mutableListOf<String>()
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (fileProviderStatistic in jsonIndexDiagnostic.projectIndexingHistory.fileProviderStatistics) {
          indexedFiles.addAll(fileProviderStatistic.filesFullyIndexedByExtensions)
        }
      }
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (scanningStatistic in jsonIndexDiagnostic.projectIndexingHistory.scanningStatistics) {
          indexedFiles.addAll(scanningStatistic.filesFullyIndexedByInfrastructureExtensions)
        }
      }
      return indexedFiles.distinct()
    }

  val numberOfIndexedByExtensionsFilesForEachProvider: Map<String, Int>
    get() {
      val indexedFiles = hashMapOf<String /* Provider name */, Int /* Number of files indexed by extensions */>()
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (scanStat in jsonIndexDiagnostic.projectIndexingHistory.scanningStatistics) {
          indexedFiles.put(scanStat.providerName, indexedFiles.getOrDefault(scanStat.providerName,
                                                                            0) + scanStat.numberOfFilesFullyIndexedByInfrastructureExtensions)
        }
      }
      return indexedFiles
    }

  val numberOfIndexedFilesByUsualIndexesPerProvider: Map<String, Int>
    get() {
      val indexedFiles = hashMapOf<String /* Provider name */, Int /* Number of files indexed by usual indexes */>()
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (indexStats in jsonIndexDiagnostic.projectIndexingHistory.fileProviderStatistics) {
          indexedFiles.put(indexStats.providerName, indexedFiles.getOrDefault(indexStats.providerName,
                                                                              0) + indexStats.totalNumberOfIndexedFiles)
        }
      }
      return indexedFiles
    }

  val numberOfFullRescanning: Int
    get() = jsonIndexDiagnostics.count { it.projectIndexingHistory.times.scanningType.isFull }

  val allIndexedFiles: Map<String, List<PortableFilePath>>
    get() {
      val indexedFiles = hashMapOf<String /* Provider name */, MutableList<PortableFilePath>>()
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (fileProviderStatistic in jsonIndexDiagnostic.projectIndexingHistory.fileProviderStatistics) {
          indexedFiles.getOrPut(fileProviderStatistic.providerName) { arrayListOf() } +=
            fileProviderStatistic.indexedFiles.orEmpty().map { it.path }
        }
      }
      return indexedFiles
    }

  override fun toString() = buildString {
    appendLine("IndexingMetrics(${ideStartResult.runContext.contextName}):")
    appendLine("IndexingMetrics(")
    for ((name, value) in ideStartResult.mainReportAttributes + toReportTimeAttributes() + toReportCountersAttributes()) {
      appendLine("  $name = $value")
    }
    appendLine(")")
  }

  fun toReportTimeAttributes(): Map<String, String> = mapOf(
    "suspended time" to StringUtil.formatDuration(suspendedTime),
    "total scan files time" to StringUtil.formatDuration(totalScanFilesTime),
    "total indexing time" to StringUtil.formatDuration(totalIndexingTime),
    "total updating time" to StringUtil.formatDuration(totalUpdatingTime),
  )

  fun toReportCountersAttributes(): Map<String, String> = mapOf(
    "number of indexed files" to totalNumberOfIndexedFiles.toString(),
    "number of scanned files" to totalNumberOfScannedFiles.toString(),
    "number of files indexed by extensions" to totalNumberOfFilesFullyIndexedByExtensions.toString(),
    "number of indexing runs" to totalNumberOfIndexingRuns.toString(),
    "number of full indexing" to numberOfFullRescanning.toString()
  )

  fun getListOfIndexingMetrics(): List<PerformanceMetrics.Metric<out Number>> {
    return listOf(
      PerformanceMetrics.Metric(metricIndexing, value = totalIndexingTime),
      PerformanceMetrics.Metric(metricScanning, value = totalScanFilesTime),
      PerformanceMetrics.Metric(metricUpdatingTime, value = totalUpdatingTime),
      PerformanceMetrics.Metric(metricNumberOfIndexedFiles, value = totalNumberOfIndexedFiles),
      PerformanceMetrics.Metric(metricNumberOfFilesIndexedByExtensions, value = totalNumberOfFilesFullyIndexedByExtensions),
      PerformanceMetrics.Metric(metricNumberOfIndexingRuns, value = totalNumberOfIndexingRuns)
    )
  }
}

fun extractIndexingMetrics(startResult: IDEStartResult): IndexingMetrics {
  val indexDiagnosticDirectory = startResult.context.paths.logsDir / "indexing-diagnostic"
  val indexDiagnosticDirectoryChildren = Files.list(indexDiagnosticDirectory).filter { it.toFile().isDirectory }.use { it.toList() }
  val projectIndexDiagnosticDirectory = indexDiagnosticDirectoryChildren.let { perProjectDirs ->
    perProjectDirs.singleOrNull() ?: error("Only one project diagnostic dir is expected: ${perProjectDirs.joinToString()}")
  }
  val jsonIndexDiagnostics = Files.list(projectIndexDiagnosticDirectory)
    .use { stream -> stream.filter { it.extension == "json" }.toList() }
    .filter { Files.size(it) > 0L }
    .map { IndexDiagnosticDumper.readJsonIndexDiagnostic(it) }
  return IndexingMetrics(startResult, jsonIndexDiagnostics)
}