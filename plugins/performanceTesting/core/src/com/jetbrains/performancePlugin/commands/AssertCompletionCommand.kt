// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.commands.CompletionCommand.CompletionItemsReport
import com.jetbrains.performancePlugin.commands.CompletionCommand.getCompletionItemsDir
import com.jetbrains.performancePlugin.utils.DataDumper
import org.jetbrains.annotations.NonNls
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries

/**
 * Command verify last completions. Provides modes: EXIST, COUNT, CONTAINS.
 * Example: %assertCompletionCommand EXIST - verify that there was at least one completion item
 * Example: %assertCompletionCommand COUNT {NUM} - verify that there were {NUM} completion items
 * Example: %assertCompletionCommand CONTAINS {COMPLETION_NAME1} {COMPLETION_NAME2} - verify that completion items contains {COMPLETION_NAME1} {COMPLETION_NAME2}
 */
class AssertCompletionCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "assertCompletionCommand"

    private const val EXIST = "EXIST"
    private const val COUNT = "COUNT"
    private const val CONTAINS = "CONTAINS"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val completionItemsDir = getCompletionItemsDir()
    val commandArgs = extractCommandArgument(PREFIX).split(" ").filterNot { it.trim() == "" }.toMutableList()
    val mode = commandArgs.removeAt(0)
    if (completionItemsDir == null) {
      throw IllegalStateException("Completion items dump dir not set")
    }
    val lastCompletion = completionItemsDir.listDirectoryEntries().maxByOrNull { it.getLastModifiedTime() }
    if (lastCompletion == null) {
      throw IllegalStateException("No completion data items file found")
    }
    val data: CompletionItemsReport = DataDumper.read(lastCompletion, CompletionItemsReport::class.java)
    if (data.totalNumber <= 0) {
      throw IllegalStateException("Expected > 0 completion variants, but got only ${data.totalNumber}")
    }
    when (mode) {
      EXIST -> return
      COUNT -> {
        val expected = commandArgs[0].toInt()
        if (data.totalNumber != expected) {
          throw IllegalStateException("Expected ${expected} completion variants, but got ${data.totalNumber}")
        }
      }
      CONTAINS -> {
        val actual = data.items.map { it.name.trim() }
        if (!actual.containsAll(commandArgs)) {
          throw IllegalStateException("Actual ${actual} does not contain expected ${commandArgs} completion variants")
        }
      }
      else -> throw IllegalArgumentException("Specified mode is neither EXIST nor COUNT nor CONTAINS")
    }
  }
}
