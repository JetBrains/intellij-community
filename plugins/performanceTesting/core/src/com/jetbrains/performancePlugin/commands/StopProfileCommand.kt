// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.profilers.Profiler.Companion.getCurrentProfilerHandler
import com.jetbrains.performancePlugin.profilers.ProfilersController

/**
 * Command stops async or yourkit profiler based on system property *integrationTests.profiler*.
 * In case when yourkit profiler was used parameters are ignored.
 * Syntax: %stopProfile [parameters]
 * Example: %stopProfile collapsed,flamegraph,traces=5000
 */
class StopProfileCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "stopProfile"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val profilerController = ProfilersController.getInstance()
    val reportPath = getCurrentProfilerHandler().stopProfileAsyncWithNotification(extractCommandArgument(PREFIX))
    if(reportPath != null) {
      profilerController.reportsPath = reportPath
      profilerController.isStoppedByScript = true
    }
  }
}
