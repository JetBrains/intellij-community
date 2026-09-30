// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.sun.management.OperatingSystemMXBean
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/**
 * Snapshot of cumulative, process-wide JVM resource counters, for attaching to the marks of
 * [MarkCommand]. The delta between two marks attributes allocation, GC, and CPU cost to the work
 * performed between them.
 *
 * The counters are process-wide, so a delta equals a phase's own cost only when that phase is the
 * dominant activity (for example, focused single-file highlighting with inspections off).
 * Treat it as a close proxy, not exact per-thread attribution.
 */
class JvmUsageSnapshot private constructor(
  private val allocatedMb: Long,
  private val gcCount: Long,
  private val gcTimeMs: Long,
  private val fullGcCount: Long,
  private val fullGcTimeMs: Long,
  private val cpuTimeMs: Long,
) {
  /**
   * The counters as OpenTelemetry attribute pairs (cumulative values, rendered as strings). Emit them
   * on two marks and subtract per key to get the cost of the work in between.
   */
  fun asAttributes(): List<Pair<String, String>> {
    return listOf(
      ALLOCATED_MB to this.allocatedMb.toString(),
      GC_COUNT to this.gcCount.toString(),
      GC_TIME_MS to this.gcTimeMs.toString(),
      FULL_GC_COUNT to this.fullGcCount.toString(),
      FULL_GC_TIME_MS to this.fullGcTimeMs.toString(),
      CPU_TIME_MS to this.cpuTimeMs.toString(),
    )
  }

  companion object {
    const val ALLOCATED_MB: String = "jvm.alloc.mb"
    const val GC_COUNT: String = "jvm.gc.count"
    const val GC_TIME_MS: String = "jvm.gc.time.ms"
    const val FULL_GC_COUNT: String = "jvm.gc.full.count"
    const val FULL_GC_TIME_MS: String = "jvm.gc.full.time.ms"
    const val CPU_TIME_MS: String = "jvm.cpu.time.ms"

    private const val BYTES_IN_MB: Long = 1024L * 1024L
    private val threadMXBean: ThreadMXBean? = ManagementFactory.getThreadMXBean() as? ThreadMXBean
    private val osMXBean: OperatingSystemMXBean? = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean

    // Stop-the-world old-generation ("full") GC collector names for Serial, Parallel, CMS, and G1,
    // mirroring AndroidStudioSystemHealthMonitor.getGcType. The IDE runs G1 by default, so
    // "G1 Old Generation" is the relevant entry. Concurrent collectors (ZGC, Shenandoah) have no
    // stop-the-world full-GC phase and are not listed, so under them the full-GC counters stay 0.
    private val FULL_GC_BEAN_NAMES: Set<String> = setOf("MarkSweepCompact", "PS MarkSweep", "ConcurrentMarkSweep", "G1 Old Generation")

    fun capture(): JvmUsageSnapshot {
      var gcCount = 0L
      var gcTimeMs = 0L
      var fullGcCount = 0L
      var fullGcTimeMs = 0L
      for (gcBean in ManagementFactory.getGarbageCollectorMXBeans()) {
        val count = gcBean.collectionCount
        val time = gcBean.collectionTime
        if (count > 0) gcCount += count
        if (time > 0) gcTimeMs += time
        if (gcBean.name in FULL_GC_BEAN_NAMES) {
          if (count > 0) fullGcCount += count
          if (time > 0) fullGcTimeMs += time
        }
      }
      // totalThreadAllocatedBytes and processCpuTime return -1 when unsupported or measurement is
      // disabled; coerce to 0 so the delta of two snapshots stays non-negative. Thread-allocation
      // measurement is enabled by default on the JBR, so this path normally yields real data.
      val allocatedBytes = threadMXBean?.totalThreadAllocatedBytes?.coerceAtLeast(0) ?: 0
      val cpuTimeNs = osMXBean?.processCpuTime?.coerceAtLeast(0) ?: 0
      return JvmUsageSnapshot(
        allocatedMb = allocatedBytes / BYTES_IN_MB,
        gcCount = gcCount,
        gcTimeMs = gcTimeMs,
        fullGcCount = fullGcCount,
        fullGcTimeMs = fullGcTimeMs,
        cpuTimeMs = cpuTimeNs / 1_000_000,
      )
    }
  }
}
