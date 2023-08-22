package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import org.jetbrains.annotations.NonNls
import java.io.IOException

class RemoveBreakpointCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "removeBreakpoint"
  }

  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    val project = context.project
    val arguments = extractCommandList(PREFIX, ",")
    if (arguments.size == 0) {
      callback.reject("Usage %removeBreakpoint all")
      return
    }
    when (arguments[0]) {
      "all" -> {
        WriteAction.runAndWait<IOException> {
          val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
          breakpointManager.allBreakpoints.forEach {
            breakpointManager.removeBreakpoint(it)
          }
          callback.setDone()
        }
      }
      else -> {
        callback.reject("Unssuported command")
      }

    }
  }
}
