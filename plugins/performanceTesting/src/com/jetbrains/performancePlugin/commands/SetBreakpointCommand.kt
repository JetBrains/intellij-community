// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import org.jetbrains.annotations.NonNls
import java.io.IOException

class SetBreakpointCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {
  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    val project = context.project
    val arguments = extractCommandList(PREFIX, ",")
    if (arguments.size == 0) {
      callback.reject("Usage %setBreakpoint &lt;line&gt; [&lt;relative_path&gt;]")
      return
    }
    val lineNumber = arguments[0].toInt()

    val file: VirtualFile
    if (arguments.size == 1) {
      val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
      if (selectedEditor == null) {
        callback.reject("No opened editor")
        return
      }
      file = selectedEditor.file
    }
    else {
      val relativePath = arguments[1]
      file = OpenFileCommand.findFile(relativePath, project) ?: error(PerformanceTestingBundle.message("command.file.not.found", relativePath))
    }
    val filePath = "file://" + file.path
    val breakpointTypes = XBreakpointUtil
      .getAvailableLineBreakpointTypes(project, XDebuggerUtilImpl().createPosition(file, lineNumber - 1)!!, null)
    if (breakpointTypes.isEmpty()) {
      callback.reject("Impossible to set breakpoint on line ${lineNumber}")
      return
    }
    WriteAction.runAndWait<IOException> {
      val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
      VirtualFileManager.getInstance().refreshAndFindFileByUrl(filePath)
      val breakpointType = breakpointTypes[0]
      val breakpoint = breakpointManager.addLineBreakpoint(breakpointType, filePath, lineNumber - 1,
                                                           breakpointType.createBreakpointProperties(file, lineNumber - 1))
      breakpointManager.updateBreakpointPresentation(breakpoint, null, null)
    }
    callback.setDone()
  }

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "setBreakpoint"
  }
}