package com.jetbrains.performancePlugin.commands

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter


/**
 * Command creates text scratch file with content.
 * Example: %createScratchFile filename.txt "this is file content"
 *
 * For file content use "_" instead " " and "\\n" instead "\n"
 */
internal class CreateScratchFile(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX = AbstractCommand.CMD_PREFIX + "createScratchFile"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    extractCommandArgument(PREFIX).split(' ').filter { it.isNotEmpty() }.also {
      if (it.size != 2) {
        context.error("Unexpected number of arguments. Should be 2 args", line)
        return
      }
      val fileName = it.first()
      val fileContent = it.last().replace("\\n", "\n").replace("_", " ")
      ScratchRootType.getInstance().createScratchFile(
        context.project,
        fileName,
        Language.findLanguageByID("TEXT"),
        fileContent
      )
    }
  }
}