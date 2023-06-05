package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

class DisableCodeVisionCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "disableCodeVision"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    Registry.get("editor.codeVision.new").setValue(false)

    return ActionCallback.DONE.toPromise()
  }
}