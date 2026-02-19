// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.asCompletableFuture
import java.io.IOException

/**
 *   Command to set breakpoint in file.
 *   Line number get from parameters.
 *   If filePath does not provide in parameters - set breakpoint in current opened editor.
 *   Support lambda and line breakpoint types. Line is a default type.
 * <p>
 *   Example: %setBreakpoint 33
 *   Example: %setBreakpoint 33, fileName.java
 *   Example: %setBreakpoint 33, lambda-type
 *   Example: %setBreakpoint 33, fileName.java, lambda-type
 */
class SetBreakpointCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {
  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    val project = context.project
    var isLambdaBreakpoint = false
    val arguments = extractCommandList(PREFIX, ",")
    if (arguments.size == 0) {
      callback.reject("Usage %setBreakpoint &lt;line&gt; [&lt;relative_path&gt;, lambda-type]")
      return
    }
    val lineNumber = arguments[0].toInt()

    val file: VirtualFile
    if (arguments.contains("lambda-type")) {
      arguments.remove("lambda-type")
      isLambdaBreakpoint = true
    }
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
      file = OpenFileCommand.findFile(relativePath, project) ?: error(
        PerformanceTestingBundle.message("command.file.not.found", relativePath))
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

      val breakpointType: XLineBreakpointType<XBreakpointProperties<*>>
      val breakpointProperties: XBreakpointProperties<*>
      if (isLambdaBreakpoint) {
        val it = XDebuggerUtilImpl.getLineBreakpointVariants(project, breakpointTypes, XSourcePositionImpl.create(file, lineNumber - 1))
          .asCompletableFuture().get()

        val lambdaVariants = it.filter { a -> !a.text.contains("Line") }

        if (lambdaVariants.isEmpty()) {
          callback.reject("Impossible to set lambda breakpoint to line $lineNumber")
          return@runAndWait
        }
        val lambdaVariant = lambdaVariants.first()

        breakpointType = lambdaVariant.type as XLineBreakpointType<XBreakpointProperties<*>>
        breakpointProperties = lambdaVariant.createProperties()!!
      }
      else {
        @Suppress("UNCHECKED_CAST")
        breakpointType = breakpointTypes.first() as XLineBreakpointType<XBreakpointProperties<*>>
        breakpointProperties = breakpointType.createBreakpointProperties(file, lineNumber - 1)!!
      }
      val breakpoint = breakpointManager.addLineBreakpoint(breakpointType, filePath, lineNumber - 1, breakpointProperties)
      breakpointManager.updateBreakpointPresentation(breakpoint, null, null)
    }
    callback.setDone()
  }

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "setBreakpoint"
  }
}