package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

class DisableCodeVisionCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "disableCodeVision"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    CodeVisionSettings.instance().codeVisionEnabled = false
    return ActionCallback.DONE.toPromise()
  }
}