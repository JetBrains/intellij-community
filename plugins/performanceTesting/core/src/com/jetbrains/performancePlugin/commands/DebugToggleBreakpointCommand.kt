package com.jetbrains.performancePlugin.commands

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

    val (virtualFile, line) = getArguments()

    val utils = XDebuggerUtilImpl.getInstance() as XDebuggerUtilImpl
    utils.toggleAndReturnLineBreakpoint(context.project, virtualFile, line, false)
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

  private fun getArguments(): Pair<VirtualFile, Int> {
    val (fileString, lineString) = extractCommandList(PREFIX, ",")
    val virtualFile = VfsUtil.findFileByIoFile(File(fileString), true) ?: error("Can't find $fileString on disk")
    return Pair(virtualFile, lineString.toInt())
  }
}