// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter

/**
 * Represents a command to drop an error during playback.
 * This command is used to simulate an error in the playback process.
 * Usage: %dropError anyText
 *
 *
 * @param text The command text.
 * @param line The line number where the command is located in the script.
 */
class DropErrorCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = PlaybackCommandCoroutineAdapter.CMD_PREFIX + "dropError"
  }
  override suspend fun doExecute(context: PlaybackContext) {
    Logger.getInstance(DropErrorCommand::class.java).error("Drop error from command", Exception(extractCommandArgument(prefix)))
  }
  override fun getName(): String {
   return "dropError"
  }
}