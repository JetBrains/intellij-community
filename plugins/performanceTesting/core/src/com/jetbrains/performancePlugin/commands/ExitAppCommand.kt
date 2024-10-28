package com.jetbrains.performancePlugin.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.jetbrains.performancePlugin.commands.MemoryCapture.Companion.capture
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.IOException

class ExitAppCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {
  companion object {
    internal const val PREFIX: @NonNls String = CMD_PREFIX + "exitApp"
  }

  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    writeExitMetricsIfNeeded()

    val arguments = text.split(' ', limit = 2)
    var forceExit = true
    if (arguments.size > 1) {
      forceExit = arguments[1].toBoolean()
    }

    ApplicationManager.getApplication().exit(forceExit, true, false)
    callback.setDone()
  }
}

private fun writeExitMetricsIfNeeded() {
  System.getProperty("idea.log.exit.metrics.file")?.let {
    writeExitMetrics(it)
  }
}

private fun writeExitMetrics(path: String) {
  val capture = capture()

  val memory = MemoryMetrics(capture.usedMb, capture.maxMb, capture.metaspaceMb)
  val metrics = ExitMetrics(memory)
  try {
    ObjectMapper().writeValue(File(path), metrics)
  }
  catch (e: IOException) {
    System.err.println("Unable to write exit metrics from " + ExitAppCommand::class.java.getSimpleName() + " " + e.message)
  }
}
