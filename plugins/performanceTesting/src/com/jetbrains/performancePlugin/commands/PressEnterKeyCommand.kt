package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.awt.AWTException
import java.awt.Robot

private val LOG = Logger.getInstance(PressEnterKeyCommand::class.java)

class PressEnterKeyCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "pressKeyEnter"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    try {
      val awtRobot = Robot()
      awtRobot.keyPress('\n'.code)
    }
    catch (e: AWTException) {
      LOG.error("Failed to press key ENTER: " + e.message)
    }
    actionCallback.setDone()
    return actionCallback.toPromise()
  }
}