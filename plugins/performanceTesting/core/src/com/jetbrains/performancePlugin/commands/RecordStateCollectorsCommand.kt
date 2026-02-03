// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.ProjectFUStateUsagesLogger
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.future.asDeferred

internal class RecordStateCollectorsCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "recordStateCollectors"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    context.project.service<ProjectFUStateUsagesLogger>().logApplicationAndProjectState()
    FeatureUsageLogger.getInstance().flush().asDeferred().join()
  }
}