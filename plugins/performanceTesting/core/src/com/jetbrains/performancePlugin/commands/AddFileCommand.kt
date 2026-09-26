// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractFileCommand(text: String, line: Int, private val numberOfArguments: Int = 2) : AbstractCallbackBasedCommand(text, line, true) {
  protected fun execute(path: Path, callback: ActionCallback, f: (VirtualFile) -> Any) {
    val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(path)
    if (virtualFile == null) {
      callback.reject("Directory or file by ${path} path from parameters was not found.")
      return
    }
    WriteAction.runAndWait<IOException> {
      f(virtualFile)
    }
    callback.setDone()
  }

  protected fun isCommandParametersRight(input: ArrayList<String>): Boolean {
    return input.size == numberOfArguments && input.all { it != "" }
  }
}

/**
 * Command to add file to project.
 * File name and path to project get from parameters.
 * Example: %addFile C:\Users\username\intellij, fileName.java
 */
class AddFileCommand(text: String, line: Int) : AbstractFileCommand(text, line) {
  @Throws(Exception::class)
  override fun execute(callback: ActionCallback,
                       context: PlaybackContext) {
    val input = extractCommandList(PREFIX, ",")
    if (!isCommandParametersRight(input)) {
      callback.reject("Command $PREFIX should have 2 non empty parameters (1 - path to directory, 2 - file name) with delimiter ','.")
      return
    }
    if (VirtualFileManager.getInstance().findFileByNioPath(Paths.get(input[0], input[1])) != null) {
      callback.reject("File by ${Paths.get(input[0], input[1])} path from parameters already exists.")
      return
    }
    LOG.info("Add file ${input[1]} ${input[0]}")
    super.execute(Paths.get(input[0]), callback) { virtualFile: VirtualFile -> virtualFile.createChildData(null, input[1]) }
  }

  companion object {
    private val LOG = logger<AddFileCommand>()
    const val PREFIX: @NonNls String = CMD_PREFIX + "addFile"
  }
}