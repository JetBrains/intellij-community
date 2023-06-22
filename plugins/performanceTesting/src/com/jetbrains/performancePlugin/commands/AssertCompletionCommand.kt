package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.util.io.lastModified
import com.jetbrains.performancePlugin.commands.CompletionCommand.CompletionItemsReport
import com.jetbrains.performancePlugin.commands.CompletionCommand.getCompletionItemsDir
import com.jetbrains.performancePlugin.utils.DataDumper
import org.jetbrains.annotations.NonNls
import kotlin.io.path.listDirectoryEntries

class AssertCompletionCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "assertCompletionCommand"
  }
  override suspend fun doExecute(context: PlaybackContext) {
    val completionItemsDir = getCompletionItemsDir()
    val commandArgs = extractCommandArgument(PREFIX).split(" ").filterNot { it.trim() == "" }
    if (completionItemsDir == null) {
      throw IllegalStateException("Completion items dump dir not set")
    }
    val lastCompletion = completionItemsDir.listDirectoryEntries().maxByOrNull { it.lastModified() }
    if (lastCompletion == null) {
      throw IllegalStateException("No completion data items file found")
    }
    val data: CompletionItemsReport = DataDumper.read(lastCompletion, CompletionItemsReport::class.java)
    if (data.totalNumber <= 0) {
      throw IllegalStateException("Expected > 0 completion variants, but got only ${data.totalNumber}")
    }
    if (commandArgs.isNotEmpty()) {
      val expected = commandArgs[0].toInt()
      if (data.totalNumber != expected) {
        throw IllegalStateException("Expected ${expected} completion variants, but got ${data.totalNumber}")
      }
    }
  }
}