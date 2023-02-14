// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.LongEventField
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.toMillis
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator
import org.jetbrains.idea.maven.statistics.MavenImportCollector

internal class WorkspaceImportStats private constructor(private val project: Project,
                                                        private val activity: StructuredIdeActivity) {
  private val configuratorsData = mutableMapOf<Class<MavenWorkspaceConfigurator>, MutableMap<LongEventField, Long>>()

  companion object {
    fun start(project: Project): WorkspaceImportStats {
      return WorkspaceImportStats(project, MavenImportCollector.WORKSPACE_IMPORT.started(project))
    }

    fun startFoldersUpdate(project: Project): WorkspaceImportStats {
      return WorkspaceImportStats(project, MavenImportCollector.WORKSPACE_FOLDERS_UPDATE.started(project))
    }
  }

  fun <T> recordPhase(phase: IdeActivityDefinition, block: (phaseActivity: StructuredIdeActivity) -> T): T {
    val phaseActivity = phase.startedWithParent(project, activity)
    try {
      return block(phaseActivity)
    }
    finally {
      phaseActivity.finished()
    }
  }

  fun recordCommitPhaseStats(durationInBackgroundNano: Long,
                             durationInWriteActionNano: Long,
                             durationOfWorkspaceUpdateCallNano: Long, attempts: Int) {
    MavenImportCollector.WORKSPACE_COMMIT_STATS.log(
      listOf(MavenImportCollector.ACTIVITY_ID.with(activity),
             MavenImportCollector.DURATION_BACKGROUND_MS.with(durationInBackgroundNano.toMillis()),
             MavenImportCollector.DURATION_WRITE_ACTION_MS.with(durationInWriteActionNano.toMillis()),
             MavenImportCollector.DURATION_OF_WORKSPACE_UPDATE_CALL_MS.with(durationOfWorkspaceUpdateCallNano.toMillis()),
             MavenImportCollector.ATTEMPTS.with(attempts)
      )
    )
  }

  fun <T> recordConfigurator(configurator: MavenWorkspaceConfigurator,
                             key: LongEventField,
                             block: () -> T): T {
    val start = System.nanoTime()
    try {
      return block()
    }
    finally {
      val durationNano = System.nanoTime() - start
      configuratorsData.computeIfAbsent(configurator.javaClass) { mutableMapOf() }.compute(key) { _, value -> (value ?: 0) + durationNano }
    }
  }

  internal fun finish(numberOfModules: Int) {
    for ((clazz, timings) in configuratorsData) {
      val logPairs = mutableListOf<EventPair<*>>()
      var totalNano = 0L
      timings.forEach { (key, nano) ->
        logPairs.add(key.with(nano.toMillis()))
        totalNano += nano
      }
      logPairs.add(MavenImportCollector.TOTAL_DURATION_MS.with(totalNano.toMillis()))
      logPairs.add(MavenImportCollector.CONFIGURATOR_CLASS.with(clazz))
      logPairs.add(MavenImportCollector.NUMBER_OF_MODULES.with(numberOfModules))
      logPairs.add(MavenImportCollector.ACTIVITY_ID.with(activity))

      MavenImportCollector.CONFIGURATOR_RUN.log(project, logPairs)
    }

    activity.finished { listOf(MavenImportCollector.NUMBER_OF_MODULES.with(numberOfModules)) }
  }
}