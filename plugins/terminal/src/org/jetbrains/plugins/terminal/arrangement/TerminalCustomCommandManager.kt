// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.arrangement

import com.intellij.openapi.application.Experiments
import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalCustomCommandHandler
import com.intellij.ui.content.Content
import com.intellij.util.Alarm
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.TerminalView
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

class TerminalCustomCommandManager internal constructor() : TerminalWatchManager() {
  private var commandListener: KeyAdapter? = null

  override fun watchTab(project: Project, content: Content) {
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, content)

    commandListener = object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        if (!Experiments.getInstance().isFeatureEnabled("terminal.custom.command.handling")) {
          super.keyPressed(e)
          return
        }

        if (e!!.keyCode == KeyEvent.VK_ENTER) {

          val command: String = fetchCommand(
            TerminalView.getWidgetByContent(content)?.terminalPanel?.terminalTextBuffer ?: return)?.substringAfter("$ ") ?: return
          val commandHandler = TerminalCustomCommandHandler.findCustomCommandHandler(command) ?: return
          alarm.cancelAllRequests()
          if (!alarm.isDisposed) alarm.addRequest({ if (commandHandler.execute(project, command)) e.consume() }, MERGE_WAIT_MILLIS)
        }
      }
    }

    TerminalView.getWidgetByContent(content)?.terminalPanel?.addCustomKeyListener(commandListener!!)
  }

  override fun unwatchTab(content: Content) {
    TerminalView.getWidgetByContent(content)?.terminalPanel?.removeCustomKeyListener(commandListener!!)
  }

  companion object {
    private const val MERGE_WAIT_MILLIS = 500

    fun fetchCommand(terminalTextBuffer: TerminalTextBuffer): String? {
      var i = terminalTextBuffer.screenLinesCount - 1
      var terminalLine: TerminalLine?
      while (i >= 0) {
        terminalLine = terminalTextBuffer.getLine(i)
        if (!terminalLine.isNul) {
          return terminalLine.text
        }
        i--
      }

      return null
    }
  }
}
