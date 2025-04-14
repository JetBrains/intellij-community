// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import org.jetbrains.annotations.NonNls

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
        XDebuggerUtilImpl.removeAllBreakpoints(project)
        callback.setDone()
      }
      else -> {
        callback.reject("Unssuported command")
      }

    }
  }
}
