// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import org.jetbrains.annotations.NonNls
import java.nio.charset.Charset

/**
 * Command checks file encoding.
 * Example: %assertEncodingFileCommand filePath expectedCharsetName - verify that filePath encoded in expectedCharsetName format
 */
class AssertEncodingFileCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "assertEncodingFileCommand"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val commandArgs = extractCommandArgument(PREFIX).split(" ")
    val filePath = commandArgs[0]
    val file = OpenFileCommand.findFile(filePath, project)
    if (file == null) {
      throw IllegalArgumentException("File not found: $filePath")
    }
    val expectedCharset = Charset.forName(commandArgs[1])
    val actualCharset = EncodingProjectManager.getInstance(project).getEncoding(file, true)
    if (!expectedCharset.equals(actualCharset)) {
      throw AssertionError("Expected $expectedCharset charset, but got $actualCharset")
    }
  }
}