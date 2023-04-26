package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.utils.DataDumper
import org.jetbrains.annotations.NonNls
import java.nio.file.Files

class AssertFindUsagesCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "assertFindUsagesCommand"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val lastUsageReport = FindUsagesCommand.getFoundUsagesJsonPath()
    if (lastUsageReport == null) {
      throw IllegalStateException("Find usages report is null")
    }
    if (!Files.exists(lastUsageReport) || !Files.isRegularFile(lastUsageReport)) {
      throw IllegalStateException("Find usages report file not exists $lastUsageReport")
    }
    val args = extractCommandArgument(PREFIX).split(" ").filterNot { it.trim() == "" }
    if (args.isEmpty()) {
      throw IllegalStateException("Provide expected count of usages")
    }

    val data: FindUsagesCommand.FoundUsagesReport = DataDumper.read(lastUsageReport, FindUsagesCommand.FoundUsagesReport::class.java)
    val expected = args[0].toInt()
    if (data.totalNumberOfUsages != expected) {
      throw IllegalStateException("Expected ${expected} find usages, but got ${data.totalNumberOfUsages}")
    }
  }
}