// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.debugger

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.NonNls
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class WaitForDebugSessionsEndCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "waitForNoDebugSessions"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val parsed = extractCommandArgument(PREFIX).toLongOrNull()
    val timeout = parsed?.milliseconds ?: 1.minutes
    val manager = XDebuggerManager.getInstance(context.project)
    val finished = withTimeoutOrNull(timeout) {
      while (manager.debugSessions.isNotEmpty()) {
        delay(500.milliseconds)
      }
    }
    if (finished == null) {
      val active = manager.debugSessions.joinToString { it.sessionName }
      error("Debug sessions did not end within $timeout. Still active: [$active]")
    }
  }
}
