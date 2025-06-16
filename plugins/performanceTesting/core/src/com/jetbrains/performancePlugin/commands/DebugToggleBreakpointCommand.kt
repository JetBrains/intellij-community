// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.tools.ide.starter.bus.logger.EventBusLoggerFactory.getLogger
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import java.io.File

private val logger = getLogger(DebugToggleBreakpointCommand::class.java)

class DebugToggleBreakpointCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {
  companion object {
    const val PREFIX: String = "${CMD_PREFIX}breakpointToggle"
  }

  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    logger.debug("Breakpoint toggle start")

    val (virtualFile, line) = getArguments(context.project)

    val utils = XDebuggerUtilImpl.getInstance() as XDebuggerUtilImpl
    utils.toggleAndReturnLineBreakpoint(context.project, virtualFile, line-1, false)
      .onError {
        logger.info("Error on breakpoint toggling $it")
        callback.reject(it.message)
      }
      .onSuccess {
        logger.info("The breakpoint was successfully toggled")
        callback.setDone()
      }

    logger.info("Breakpoint toggle finish")
  }

  private fun getArguments(project: Project): Pair<VirtualFile, Int> {
    val (fileString, lineString) = extractCommandList(PREFIX, ",")
    val virtualFile = findFile(project, fileString)
    return Pair(virtualFile, lineString.toInt())
  }

  private fun findFile(project: Project, filePath: String): VirtualFile {
    val absolutePath = VfsUtil.findFileByIoFile(File(filePath), true)
    if (absolutePath != null) return absolutePath

    val relativePath = project.getBaseDirectories().firstNotNullOfOrNull { it.findFileByRelativePath(filePath) }
    return relativePath ?: error("Can't find $filePath on disk")
  }
}