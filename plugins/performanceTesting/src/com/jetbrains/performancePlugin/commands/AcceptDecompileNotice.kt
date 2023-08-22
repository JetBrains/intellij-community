package com.jetbrains.performancePlugin.commands

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import org.jetbrains.annotations.NonNls

class AcceptDecompileNotice(text: String, line: Int): PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "acceptDecompileNotice"
  }
  override suspend fun doExecute(context: PlaybackContext) {
    PropertiesComponent.getInstance().setValue("decompiler.legal.notice.accepted", true)
  }
}