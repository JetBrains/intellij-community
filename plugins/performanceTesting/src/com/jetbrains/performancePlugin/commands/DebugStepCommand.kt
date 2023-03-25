package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import org.jetbrains.annotations.NonNls
import java.io.IOException

class DebugStepCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {
  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    val debugStepType = extractCommandArgument(PREFIX)
    val debugSessions = XDebuggerManager.getInstance(context.project).debugSessions
    if (debugSessions.isEmpty()) {
      callback.reject("Debug process was not started")
      return
    }
    if (debugSessions.size > 1) {
      callback.reject("Currently running ${debugSessions.size} debug processes")
      return
    }

    debugSessions.first().debugProcess.session.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        super.sessionPaused()
        callback.setDone()
      }
    })

    WriteAction.runAndWait<IOException> {
      when (debugStepType) {
        "OVER" -> debugSessions[0].stepOver(false)
        "INTO" -> debugSessions[0].stepInto()
        "OUT" -> debugSessions[0].stepOut()
        else -> {
          callback.reject("Unknown special character. Please use: OVER, INTO or OUT")
        }
      }
    }
  }

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "debugStep"
  }
}