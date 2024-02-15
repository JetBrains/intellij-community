package com.jetbrains.performancePlugin.commands

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter

internal class CreateScratchFile(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX = AbstractCommand.CMD_PREFIX + "createScratchFile"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    text.replace(PREFIX, "").split(' ').filter { it.isNotEmpty() }.apply {
      ScratchRootType.getInstance().createScratchFile(
        context.project,
        this[0],
        Language.findLanguageByID("TEXT"),
        this[1].replace("\\n", "\n").replace("_", " ")
      )
    }
  }
}