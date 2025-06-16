// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls
import java.nio.file.Paths

/**
 * Command delete file from project if file exists.
 * File name and path to project get from parameters.
 * Example: %deleteFile C:\Users\username\intellij, fileName.java
 */
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
    private val LOG = logger<DeleteFileCommand>()
    const val PREFIX: @NonNls String = CMD_PREFIX + "deleteFile"
  }
}