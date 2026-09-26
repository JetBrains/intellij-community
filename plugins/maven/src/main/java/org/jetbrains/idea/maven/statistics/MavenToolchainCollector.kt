// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenSyncSession
import org.jetbrains.idea.maven.toolchains.ToolchainResolverSession

@ApiStatus.Internal
object MavenToolchainCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  val GROUP = EventLogGroup("maven.toolchains", 1)

  private val REQUIREMENTS_COUNT = EventFields.RoundedInt("requirements_count")
  private val RESOLVED_COUNT = EventFields.RoundedInt("resolved_count")
  private val MISSED_COUNT = EventFields.RoundedInt("missed_count")

  /**
   * Fired once per sync when at least one toolchain requirement is detected in the project.
   * Tracks how many toolchain requirements were found, successfully resolved, and missed.
   */
  private val SYNC_STATS = GROUP.registerVarargEvent("sync_stats", REQUIREMENTS_COUNT, RESOLVED_COUNT, MISSED_COUNT)

  /**
   * Fired when the user invokes the "Add SDK to toolchains.xml" quick fix shown in the sync output.
   */
  @JvmField
  val QUICKFIX_INVOKED = GROUP.registerEvent("quickfix_invoked")

  fun logSyncStats(syncSession: MavenSyncSession) {
    val toolchainSession = ToolchainResolverSession.forSession(syncSession)
    val missedCount = toolchainSession.unresolved().size
    val resolvedCount = toolchainSession.resolvedCount()
    val totalCount = resolvedCount + missedCount
    if (totalCount > 0) {
      SYNC_STATS.log(
        syncSession.project,
        REQUIREMENTS_COUNT.with(totalCount),
        RESOLVED_COUNT.with(resolvedCount),
        MISSED_COUNT.with(missedCount)
      )
    }
  }
}
