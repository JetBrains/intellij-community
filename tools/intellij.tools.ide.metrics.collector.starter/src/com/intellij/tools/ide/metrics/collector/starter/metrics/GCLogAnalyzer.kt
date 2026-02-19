package com.intellij.tools.ide.metrics.collector.starter.metrics

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.FileSystem.listDirectoryEntriesQuietly
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.text.NumberFormat
import java.util.Locale
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

class GCLogAnalyzer(private val ideStartResult: IDEStartResult) {
  companion object {
    private const val gcViewerUrl = "https://packages.jetbrains.team/files/p/ij/intellij-dependencies/gcviewer/gcviewer-1.37-05122022.jar"
  }

  fun getGCMetrics(
    requestedMetrics: Array<String> = arrayOf("gcPause", "fullGCPause", "gcPauseCount", "totalHeapUsedMax", "freedMemoryByGC", "freedMemoryByFullGC", "freedMemory"),
  ): Iterable<PerformanceMetrics.Metric> {
    val gcLogFile = (ideStartResult.runContext.reportsDir / "gcLog.log").toFile()
    return if (gcLogFile.exists()) {
      processGCSummary(findExistingSummary() ?: generateGCSummaryFile(), requestedMetrics) + extractAdditionalGcMetrics(gcLogFile)
    }
    else listOf()
  }

  private fun findExistingSummary() = ideStartResult.runContext.reportsDir.listDirectoryEntriesQuietly()?.firstOrNull { file ->
    file.name.startsWith("gcSummary_")
  }

  fun generateGCSummaryFile(): Path {
    val summaryFile = ideStartResult.runContext.reportsDir.resolve("gcSummary_${System.currentTimeMillis()}.log")

    val toolsDir = GlobalPaths.instance.getCacheDirectoryFor("tools")

    val gcViewerPath = toolsDir.resolve(gcViewerUrl.substringAfterLast('/'))
    HttpClient.downloadIfMissing(gcViewerUrl, gcViewerPath)

    runGCViewer(ideStartResult.runContext, gcViewerPath, summaryFile)

    return summaryFile
  }

  private fun runGCViewer(context: IDERunContext, gcViewer: Path, gcSummary: Path) {
    val gcLogPath = (context.reportsDir / "gcLog.log").toAbsolutePath()
    val logsFiles = Files.list(context.reportsDir).filter { it.fileName != null && it.name.startsWith("gcLog.log") }.toList()
    if (!logsFiles.isEmpty()) {
      val paths = logsFiles.joinToString(separator = ";", transform = { it.pathString })

      // reuse current Java executable, or let's hope 'java' exists somewhere in PATH and it is compatible
      val command = ProcessHandle.current().info().command().orElse(null)
      val javaCommand = if (command.isNullOrBlank()) "java" else command

      try {
        ProcessExecutor(
          "gcviewer",
          workDir = gcViewer.parent, timeout = 1.minutes,
          args = listOf(javaCommand, "-jar", gcViewer.toAbsolutePath().toString(), paths, gcSummary.toAbsolutePath().toString())
        ).start()
      }
      catch (t: Throwable) {
        println("gcviewer process failed by: ${t.message}")
      }

    }
    else {
      println("$gcLogPath doesn't exists")
    }
  }

  private fun processGCSummary(gcSummary: Path, requestedMetrics: Array<String>): List<PerformanceMetrics.Metric> {
    val gcMetrics = mutableListOf<PerformanceMetrics.Metric>()
    val format = NumberFormat.getNumberInstance(Locale.getDefault())
    if (!gcSummary.exists()) {
      println("$gcSummary doesn't exists")
      return gcMetrics
    }
    gcSummary.toFile().forEachLine { line ->
      val splitLine = line.split(";")
      if (splitLine.size < 3) {
        return@forEachLine
      }
      val parameter = splitLine[0].trim()
      val value = format.runCatching { parse(splitLine[1].trim()).toDouble() }.getOrNull()
      if (value == null) {
        return@forEachLine
      }

      val type = when (val type = splitLine[2].trim()) {
        "bool" -> return@forEachLine
        "" -> ""
        else -> type
      }
      if (parameter in requestedMetrics) {
        when (type) {
          "-", "M" -> {
            gcMetrics.add(PerformanceMetrics.Metric.newCounter(parameter, value.toInt()))
          }
          "s" -> {
            gcMetrics.add(PerformanceMetrics.Metric.newDuration(parameter, (value * 1000).toInt()))
          }
          else -> {
            println("Unknown type: $type")
          }
        }
      }
    }
    return gcMetrics
  }

  /**
   * Extracts more GC metrics, in addition to [generateGCSummaryFile]/[processGCSummary].
   *
   * `g1gcConcurrentMarkCycles` - the number of G1GC concurrent mark cycles
   *
   * `g1gcConcurrentMarkTimeMs` - the total time spent in G1GC concurrent mark cycles.
   * This is not a GC pause, but a time spent in concurrent GC activity.
   * This is not a "CPU time" because the concurrent mark cycle uses multiple CPU cores
   * while this metric accounts only for the total time regardless of how many cores are involved.
   *
   * `g1gcHeapShrinkageCount` - the number of events when GC shrinks the size of the heap.
   *
   * `g1gcHeapShrinkageMegabytes` - the total amount of memory (in MB) reduced by heap shrinkage.
   * Note that GC can change the heap size many times: grow and shrink, grow and shrink, and so on.
   * So the number can be way bigger than the heap size.
   */
  private fun extractAdditionalGcMetrics(gcLogFile: File): List<PerformanceMetrics.Metric> {
    var concurrentMarkCycleCount = 0
    var concurrentMarkCycleTimeMicrosecondsSum = 0
    var heapShrinkageCount = 0
    var heapShrinkageMegabytes = 0
    var lastHeapSize = -1
    gcLogFile.forEachLine { line ->
      extractConcurrentMarkCycleTimeMicroseconds(line)?.let { concurrentMarkCycleTimeMicroseconds ->
        concurrentMarkCycleCount++
        concurrentMarkCycleTimeMicrosecondsSum += concurrentMarkCycleTimeMicroseconds
      }
      extractHeapSizeMegabytes(line)?.let { heapSize ->
        if (lastHeapSize != -1) {
          if (heapSize < lastHeapSize) {
            heapShrinkageCount++
            heapShrinkageMegabytes += lastHeapSize - heapSize
          }
        }
        lastHeapSize = heapSize
      }
    }

    return listOf(
      PerformanceMetrics.Metric.newCounter("g1gcConcurrentMarkCycles", concurrentMarkCycleCount),
      PerformanceMetrics.Metric.newDuration("g1gcConcurrentMarkTimeMs", concurrentMarkCycleTimeMicrosecondsSum / 1000),
      PerformanceMetrics.Metric.newCounter("g1gcHeapShrinkageCount", heapShrinkageCount),
      PerformanceMetrics.Metric.newCounter("g1gcHeapShrinkageMegabytes", heapShrinkageMegabytes),
    )
  }

  private fun extractConcurrentMarkCycleTimeMicroseconds(line: String): Int? {
    val matchResult = CONCURRENT_MARK_CYCLE_REGEX.find(line)
    return matchResult?.groupValues?.get(1)
      ?.replace(",", "")
      ?.replace(".", "")
      ?.toIntOrNull()
  }

  private fun extractHeapSizeMegabytes(line: String): Int? {
    val matchResult = HEAP_SIZE_REGEX.find(line)
    return matchResult?.groupValues?.get(1)
      ?.toIntOrNull()
  }
}

// Example: [0,444s][info][gc          ] GC(4) Concurrent Mark Cycle 13,954ms
private val CONCURRENT_MARK_CYCLE_REGEX = """Concurrent Mark Cycle (\d+[.,]\d\d\d)ms$""".toRegex()

// Example: [12,136s][info][gc          ] GC(38) Pause Young (Prepare Mixed) (G1 Evacuation Pause) 625M->405M(702M) 7,220ms
// Example: [11,619s][info][gc          ] GC(37) Pause Remark 443M->435M(702M) 34,541ms
private val HEAP_SIZE_REGEX = """ Pause .* \d+M->\d+M\((\d+)M\) """.toRegex()
