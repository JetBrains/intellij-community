package com.jetbrains.performancePlugin.commands

import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import org.jetbrains.annotations.NonNls
import java.io.IOException

class StopDebugProcessCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {

  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    val debugSessions = XDebuggerManager.getInstance(context.project).debugSessions
    if (debugSessions.isEmpty()) {
      callback.reject("Debug process was not started")
      return
    }
    if (debugSessions.size > 1) {
      callback.reject("Currently running ${debugSessions.size} debug processes")
      return
    }

    WriteAction.runAndWait<IOException> {
      val selectedContent = RunContentManager.getInstance(context.project).getSelectedContent()
      ExecutionManagerImpl.stopProcess(selectedContent)
      callback.setDone()
    }
  }

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "stopDebugProcess"
  }
}