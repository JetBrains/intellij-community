// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.future.asDeferred

class RecordCounterCollectorBaselinesCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  override suspend fun doExecute(context: PlaybackContext) {
    FUCounterUsageLogger.getInstance().logRegisteredGroups().asDeferred().join()
  }

  companion object {
    const val PREFIX = CMD_PREFIX + "recordRegisteredCounterGroups"
  }
}