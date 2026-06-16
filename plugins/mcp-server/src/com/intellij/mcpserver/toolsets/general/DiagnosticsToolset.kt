// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.general

import com.intellij.diagnostic.ThreadDumper
import com.intellij.diagnostic.isCoroutineDumpEnabled
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue.FALSE
import com.intellij.mcpserver.annotations.McpToolHintValue.TRUE
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.projectOrNull
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.lang.management.ManagementFactory
import java.lang.management.MemoryUsage
import java.lang.management.ThreadInfo
import java.lang.management.ThreadMXBean
import kotlin.time.Duration.Companion.milliseconds

private const val DIAGNOSTICS_ENABLED_PROPERTY = "idea.diagnostics.mcp.enabled"
private const val DEFAULT_SAMPLE_MILLIS = 1000
private const val MAX_SAMPLE_MILLIS = 30_000
private const val DEFAULT_TOP_THREAD_COUNT = 25
private const val MAX_TOP_THREAD_COUNT = 200
private const val DEFAULT_MAX_DUMP_CHARS = 200_000
private const val MAX_DUMP_CHARS = 2_000_000
private const val MAX_STACK_FRAMES_PER_THREAD = 16

class DiagnosticsToolset : McpToolset {
  override fun isEnabled(): Boolean = System.getProperty(DIAGNOSTICS_ENABLED_PROPERTY).toBoolean()

  @McpToolHints(readOnlyHint = TRUE, openWorldHint = FALSE)
  @McpTool
  @McpDescription("""
    |Captures cheap diagnostics from the running IDE process.
    |Use this when the IDE feels slow or stuck and you need current JVM/process metrics, top CPU threads, thread states, and thread/coroutine dumps.
    |The toolset is disabled unless the IDE is started with `-D$DIAGNOSTICS_ENABLED_PROPERTY=true`.
    |CPU data is based on per-thread CPU-time deltas sampled inside the IDE process, not a full profiler recording.
  """)
  suspend fun get_ide_diagnostics(
    @McpDescription("CPU sampling window in milliseconds. Values are clamped to 0..$MAX_SAMPLE_MILLIS. Use 0 for an immediate snapshot.")
    sampleMillis: Int = DEFAULT_SAMPLE_MILLIS,
    @McpDescription("Maximum number of CPU-ranked threads to return. Values are clamped to 1..$MAX_TOP_THREAD_COUNT.")
    topThreadCount: Int = DEFAULT_TOP_THREAD_COUNT,
    @McpDescription("Whether to include the raw IntelliJ thread dump text. Defaults to true.")
    includeRawDump: Boolean = true,
    @McpDescription("Maximum raw dump characters to return. Values are clamped to 0..$MAX_DUMP_CHARS.")
    maxDumpChars: Int = DEFAULT_MAX_DUMP_CHARS,
    @McpDescription("Whether coroutine dump stack frames with little diagnostic value should be stripped.")
    stripCoroutineDump: Boolean = true,
  ): IdeDiagnosticsResult {
    val normalizedSampleMillis = sampleMillis.coerceIn(0, MAX_SAMPLE_MILLIS)
    val normalizedTopThreadCount = topThreadCount.coerceIn(1, MAX_TOP_THREAD_COUNT)
    val normalizedMaxDumpChars = maxDumpChars.coerceIn(0, MAX_DUMP_CHARS)

    val threadMxBean = ManagementFactory.getThreadMXBean()
    val restoreThreadCpuTime = enableThreadCpuTimeIfPossible(threadMxBean)
    try {
      val before = collectThreadSamples(threadMxBean)
      if (normalizedSampleMillis > 0) {
        delay(normalizedSampleMillis.milliseconds)
      }
      val after = collectThreadSamples(threadMxBean)
      val threadInfos = ThreadDumper.getThreadInfos(threadMxBean, true)
      val rawDumpResult = if (includeRawDump) rawDump(threadInfos, stripCoroutineDump, normalizedMaxDumpChars) else RawDumpResult(null, false)
      return IdeDiagnosticsResult(
        capturedAtEpochMillis = System.currentTimeMillis(),
        sampleMillis = normalizedSampleMillis,
        ide = ideInfo(currentCoroutineContext().projectOrNull),
        jvm = jvmInfo(),
        process = processInfo(),
        memory = memoryInfo(),
        garbageCollectors = garbageCollectorInfo(),
        threads = threadSummary(threadMxBean, threadInfos),
        coroutineDumpEnabled = isCoroutineDumpEnabled(),
        topCpuThreads = topCpuThreads(before, after, normalizedTopThreadCount),
        rawDump = rawDumpResult.text,
        rawDumpTruncated = rawDumpResult.truncated,
      )
    }
    finally {
      restoreThreadCpuTime?.let { threadMxBean.isThreadCpuTimeEnabled = it }
    }
  }

  @Serializable
  data class IdeDiagnosticsResult(
    val capturedAtEpochMillis: Long,
    val sampleMillis: Int,
    val ide: IdeInfo,
    val jvm: JvmInfo,
    val process: ProcessInfo,
    val memory: MemoryInfo,
    val garbageCollectors: List<GarbageCollectorInfo>,
    val threads: ThreadSummary,
    val coroutineDumpEnabled: Boolean,
    val topCpuThreads: List<ThreadCpuSample>,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val rawDump: String? = null,
    val rawDumpTruncated: Boolean,
  )

  @Serializable
  data class IdeInfo(
    val fullApplicationName: String,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val projectName: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val projectBasePath: String? = null,
  )

  @Serializable
  data class JvmInfo(
    val name: String,
    val vendor: String,
    val version: String,
    val uptimeMillis: Long,
  )

  @Serializable
  data class ProcessInfo(
    val availableProcessors: Int,
    val processCpuLoad: Double,
    val systemCpuLoad: Double,
    val processCpuTimeNanos: Long,
  )

  @Serializable
  data class MemoryInfo(
    val heap: MemoryUsageInfo,
    val nonHeap: MemoryUsageInfo,
    val pendingFinalizationCount: Int,
  )

  @Serializable
  data class MemoryUsageInfo(
    val initBytes: Long,
    val usedBytes: Long,
    val committedBytes: Long,
    val maxBytes: Long,
  )

  @Serializable
  data class GarbageCollectorInfo(
    val name: String,
    val collectionCount: Long,
    val collectionTimeMillis: Long,
  )

  @Serializable
  data class ThreadSummary(
    val liveThreadCount: Int,
    val peakThreadCount: Int,
    val daemonThreadCount: Int,
    val totalStartedThreadCount: Long,
    val stateCounts: Map<String, Int>,
  )

  @Serializable
  data class ThreadCpuSample(
    val id: Long,
    val name: String,
    val state: String,
    val cpuDeltaNanos: Long,
    val userDeltaNanos: Long,
    val blockedCountDelta: Long,
    val waitedCountDelta: Long,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val lockName: String? = null,
    val stackTrace: List<String>,
  )
}

private data class RawDumpResult(val text: String?, val truncated: Boolean)

private data class ThreadSample(
  val id: Long,
  val name: String,
  val state: String,
  val cpuTimeNanos: Long,
  val userTimeNanos: Long,
  val blockedCount: Long,
  val waitedCount: Long,
  val lockName: String?,
  val stackTrace: List<String>,
)

private fun enableThreadCpuTimeIfPossible(threadMxBean: ThreadMXBean): Boolean? {
  if (!threadMxBean.isThreadCpuTimeSupported) return null
  val previous = threadMxBean.isThreadCpuTimeEnabled
  if (!previous) {
    threadMxBean.isThreadCpuTimeEnabled = true
  }
  return previous
}

private fun collectThreadSamples(threadMxBean: ThreadMXBean): Map<Long, ThreadSample> {
  return ThreadDumper.getThreadInfos(threadMxBean, false).associate { info ->
    info.threadId to ThreadSample(
      id = info.threadId,
      name = info.threadName,
      state = info.threadState.name,
      cpuTimeNanos = threadCpuTime(threadMxBean, info.threadId),
      userTimeNanos = threadUserTime(threadMxBean, info.threadId),
      blockedCount = info.blockedCount,
      waitedCount = info.waitedCount,
      lockName = info.lockName,
      stackTrace = info.stackTrace.take(MAX_STACK_FRAMES_PER_THREAD).map { it.toString() },
    )
  }
}

private fun threadCpuTime(threadMxBean: ThreadMXBean, threadId: Long): Long {
  if (!threadMxBean.isThreadCpuTimeSupported || !threadMxBean.isThreadCpuTimeEnabled) return -1
  return threadMxBean.getThreadCpuTime(threadId)
}

private fun threadUserTime(threadMxBean: ThreadMXBean, threadId: Long): Long {
  if (!threadMxBean.isThreadCpuTimeSupported || !threadMxBean.isThreadCpuTimeEnabled) return -1
  return threadMxBean.getThreadUserTime(threadId)
}

private fun topCpuThreads(before: Map<Long, ThreadSample>, after: Map<Long, ThreadSample>, limit: Int): List<DiagnosticsToolset.ThreadCpuSample> {
  return after.values.map { current ->
    val previous = before[current.id]
    DiagnosticsToolset.ThreadCpuSample(
      id = current.id,
      name = current.name,
      state = current.state,
      cpuDeltaNanos = positiveDelta(previous?.cpuTimeNanos, current.cpuTimeNanos),
      userDeltaNanos = positiveDelta(previous?.userTimeNanos, current.userTimeNanos),
      blockedCountDelta = positiveDelta(previous?.blockedCount, current.blockedCount),
      waitedCountDelta = positiveDelta(previous?.waitedCount, current.waitedCount),
      lockName = current.lockName,
      stackTrace = current.stackTrace,
    )
  }.sortedWith(compareByDescending<DiagnosticsToolset.ThreadCpuSample> { it.cpuDeltaNanos }.thenBy { it.name }).take(limit)
}

private fun positiveDelta(before: Long?, after: Long): Long {
  if (before == null || before < 0 || after < 0) return -1
  return (after - before).coerceAtLeast(0)
}

private fun rawDump(threadInfos: Array<ThreadInfo>, stripCoroutineDump: Boolean, maxDumpChars: Int): RawDumpResult {
  if (maxDumpChars == 0) return RawDumpResult("", true)
  val dump = ThreadDumper.getThreadDumpInfo(threadInfos, stripCoroutineDump).rawDump
  if (dump.length <= maxDumpChars) return RawDumpResult(dump, false)
  return RawDumpResult(dump.take(maxDumpChars), true)
}

private fun ideInfo(project: Project?): DiagnosticsToolset.IdeInfo {
  return DiagnosticsToolset.IdeInfo(
    fullApplicationName = ApplicationInfo.getInstance().fullApplicationName,
    projectName = project?.name,
    projectBasePath = project?.basePath,
  )
}

private fun jvmInfo(): DiagnosticsToolset.JvmInfo {
  val runtimeMxBean = ManagementFactory.getRuntimeMXBean()
  return DiagnosticsToolset.JvmInfo(
    name = System.getProperty("java.vm.name").orEmpty(),
    vendor = System.getProperty("java.vm.vendor").orEmpty(),
    version = System.getProperty("java.vm.version").orEmpty(),
    uptimeMillis = runtimeMxBean.uptime,
  )
}

private fun processInfo(): DiagnosticsToolset.ProcessInfo {
  val osMxBean = ManagementFactory.getOperatingSystemMXBean()
  val extended = osMxBean as? com.sun.management.OperatingSystemMXBean
  return DiagnosticsToolset.ProcessInfo(
    availableProcessors = osMxBean.availableProcessors,
    processCpuLoad = extended?.processCpuLoad ?: -1.0,
    systemCpuLoad = extended?.cpuLoad ?: -1.0,
    processCpuTimeNanos = extended?.processCpuTime ?: -1,
  )
}

@Suppress("DEPRECATION")
private fun memoryInfo(): DiagnosticsToolset.MemoryInfo {
  val memoryMxBean = ManagementFactory.getMemoryMXBean()
  return DiagnosticsToolset.MemoryInfo(
    heap = memoryUsageInfo(memoryMxBean.heapMemoryUsage),
    nonHeap = memoryUsageInfo(memoryMxBean.nonHeapMemoryUsage),
    pendingFinalizationCount = memoryMxBean.objectPendingFinalizationCount,
  )
}

private fun memoryUsageInfo(memoryUsage: MemoryUsage): DiagnosticsToolset.MemoryUsageInfo {
  return DiagnosticsToolset.MemoryUsageInfo(
    initBytes = memoryUsage.init,
    usedBytes = memoryUsage.used,
    committedBytes = memoryUsage.committed,
    maxBytes = memoryUsage.max,
  )
}

private fun garbageCollectorInfo(): List<DiagnosticsToolset.GarbageCollectorInfo> {
  return ManagementFactory.getGarbageCollectorMXBeans().map { bean ->
    DiagnosticsToolset.GarbageCollectorInfo(
      name = bean.name,
      collectionCount = bean.collectionCount,
      collectionTimeMillis = bean.collectionTime,
    )
  }
}

private fun threadSummary(threadMxBean: ThreadMXBean, threadInfos: Array<ThreadInfo>): DiagnosticsToolset.ThreadSummary {
  return DiagnosticsToolset.ThreadSummary(
    liveThreadCount = threadMxBean.threadCount,
    peakThreadCount = threadMxBean.peakThreadCount,
    daemonThreadCount = threadMxBean.daemonThreadCount,
    totalStartedThreadCount = threadMxBean.totalStartedThreadCount,
    stateCounts = threadInfos.groupingBy { it.threadState.name }.eachCount().toSortedMap(),
  )
}
