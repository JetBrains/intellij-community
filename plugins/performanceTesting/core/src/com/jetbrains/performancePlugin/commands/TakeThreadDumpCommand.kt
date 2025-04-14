// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files

/**
 * Command takes thread dump.
 * Thread dump will be stored under the log dir of IDE, the file name is threadDump_before_exit.txt
 *
 *
 * Syntax: %takeThreadDump
*/
class TakeThreadDumpCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "takeThreadDump"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val threadDump = ThreadDumper.dumpThreadsToString()
    val threadDumpBeforeExit = PathManager.getLogDir().resolve("threadDump_before_exit.txt")
    if (!Files.exists(threadDumpBeforeExit)) {
      withContext(Dispatchers.IO) {
        Files.createFile(threadDumpBeforeExit)
      }
    }

    withContext(Dispatchers.IO) {
      Files.writeString(threadDumpBeforeExit, threadDump)
    }
  }
}


