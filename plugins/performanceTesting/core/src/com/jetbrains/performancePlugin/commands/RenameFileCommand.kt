// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.annotations.NonNls
import java.nio.file.Paths


/**
 * Command to rename file in project.
 * File name and path to project get from parameters.
 * Example: %addFile C:\Users\username\intellij, oldFileName.java newFileName.java
 */
class RenameFileCommand(text: String, line: Int) : AbstractFileCommand(text, line, 3) {
  @Throws(Exception::class)
  override fun execute(callback: ActionCallback,
                       context: PlaybackContext) {
    val input = extractCommandList(PREFIX, ",")
    if (!isCommandParametersRight(input)) {
      callback.reject(
        "Command $PREFIX should have 3 non empty parameters (1 - path to directory, 2 - old file name, 3 - new file name) with delimiter ','.")
      return
    }
    val path = input[0]
    val oldName = input[1]
    val newName = input[2]
    if (VirtualFileManager.getInstance().findFileByNioPath(Paths.get(path, newName)) != null) {
      callback.reject("File by ${Paths.get(path, newName)} path from parameters already exists.")
      return
    }
    LOG.info("Rename file $oldName to  $newName (path: $path)")
    super.execute(Paths.get(path, oldName), callback) { virtualFile: VirtualFile -> virtualFile.rename(null, newName) }
  }

  companion object {
    private val LOG = logger<AddFileCommand>()
    const val PREFIX: @NonNls String = CMD_PREFIX + "renameFile"
  }
}