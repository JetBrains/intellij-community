// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import org.jetbrains.annotations.NonNls
import java.io.IOException

class SetBreakpointCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {
  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    val lineNumber = extractCommandArgument(PREFIX).toInt() - 1
    val project = context.project
    val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
    if (selectedEditor == null) {
      callback.reject("No opened editor")
      return
    }
    val filePath = "file://" + selectedEditor.file.path
    val breakpointTypes = XBreakpointUtil
      .getAvailableLineBreakpointTypes(project, XDebuggerUtilImpl().createPosition(selectedEditor.file, lineNumber)!!, null)
    if (breakpointTypes.isEmpty()) {
      callback.reject("Impossible to set breakpoint on line ${lineNumber + 1}")
      return
    }
    WriteAction.runAndWait<IOException> {
      val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
      VirtualFileManager.getInstance().refreshAndFindFileByUrl(filePath)
      val breakpointType = breakpointTypes[0]
      val breakpoint = breakpointManager.addLineBreakpoint(breakpointType, filePath, lineNumber,
                                                           breakpointType.createBreakpointProperties(selectedEditor.file, lineNumber))
      breakpointManager.updateBreakpointPresentation(breakpoint, null, null)
    }
    callback.setDone()
  }

  companion object {
    private val LOG = Logger.getInstance(SetBreakpointCommand::class.java)
    const val PREFIX: @NonNls String = CMD_PREFIX + "setBreakpoint"
  }
}