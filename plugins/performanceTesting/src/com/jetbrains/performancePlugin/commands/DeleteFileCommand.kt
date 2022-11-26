package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls
import java.nio.file.Paths

class DeleteFileCommand(text: String, line: Int) : AbstractFileCommand(text, line) {
  @Throws(Exception::class)
  override fun execute(callback: ActionCallback,
                       context: PlaybackContext) {
    val input = extractCommandList(PREFIX, ",")
    if (!isCommandParametersRight(input)) {
      callback.reject("Command ${PREFIX} should have 2 non empty parameters (1 - path to directory, 2 - file name) with delimiter ','.")
      return
    }
    LOG.info("Delete file " + input[1] + " " + input[0])
    super.execute(Paths.get(input[0], input[1]), callback) { virtualFile: VirtualFile -> virtualFile.delete(null) }
  }

  companion object {
    private val LOG = Logger.getInstance(DeleteFileCommand::class.java)
    const val PREFIX: @NonNls String = CMD_PREFIX + "deleteFile"
  }
}