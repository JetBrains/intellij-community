// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import com.jetbrains.performancePlugin.utils.DataDumper
import com.sampullara.cli.Args
import org.jetbrains.annotations.NonNls
import java.nio.file.Files

/**
 * Verifies that a particular option is present in the list. Find usages option properties: text, filePath, line.
 * Example: %assertFindUsagesEntryCommand [-text code line]|[-filePath a/b/test.kt]|[-line 12]
 */
class AssertFindUsagesEntryCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "assertFindUsagesEntryCommand"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val lastUsageReport = FindUsagesDumper.getFoundUsagesJsonPath() ?: throw IllegalStateException("Find usages report is null")
    if (!Files.exists(lastUsageReport) || !Files.isRegularFile(lastUsageReport)) {
      throw IllegalStateException("Find usages report file not exists $lastUsageReport")
    }

    val options = AssertFindUsagesEntryArguments()
    Args.parse(options, extractCommandArgument(PREFIX).split("|").flatMap { it.split(" ", limit = 2) }.toTypedArray(), false)

    if (options.text == null && options.filePath == null) {
      throw IllegalStateException("Provide expected test or position of the find usages option.")
    }
    val data: FindUsagesDumper.FoundUsagesReport = DataDumper.read(lastUsageReport, FindUsagesDumper.FoundUsagesReport::class.java)
    val relativePath = PortableFilePath.RelativePath(PortableFilePath.ProjectRoot, options.filePath)
    data.usages.find {
      var isMatch = true
      if (options.text != null && options.text != it.text) {
        isMatch = false
      }
      if (options.filePath != null && !relativePath.equals(it.portableFilePath)) {
        isMatch = false
      }
      if (options.line != null && options.line != it.line) {
        isMatch = false
      }

      return@find isMatch
    } ?: throw IllegalStateException("Expected FindUsages option wasn't found in the list.")
  }
}