// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.NonNls
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class StopDebugProcessCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "stopDebugProcess"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val debugSessions = XDebuggerManager.getInstance(context.project).debugSessions
    if (debugSessions.isEmpty()) throw IllegalStateException("Debug process was not started")
    if (debugSessions.size > 1) throw IllegalStateException("Currently running ${debugSessions.size} debug processes")

    var selectedContent: RunContentDescriptor? = null
    edtWriteAction {
      selectedContent = RunContentManager.getInstance(context.project).getSelectedContent()
      ExecutionManagerImpl.stopProcess(selectedContent)
    }
    withTimeout(1.minutes) {
      while (selectedContent == null || selectedContent?.processHandler?.isProcessTerminated == false) {
        delay(500.milliseconds)
      }
    }
  }
}