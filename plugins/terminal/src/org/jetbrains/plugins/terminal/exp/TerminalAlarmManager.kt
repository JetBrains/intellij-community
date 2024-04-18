// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import java.awt.Toolkit

internal class TerminalAlarmManager(private val settings: JBTerminalSystemSettingsProviderBase) : ShellCommandListener {
  private var commandIsRunning: Boolean = false

  fun beep() {
    if (commandIsRunning && settings.audibleBell()) {
      Toolkit.getDefaultToolkit().beep()
    }
  }

  override fun commandStarted(command: String) {
    commandIsRunning = true
  }

  override fun commandFinished(event: CommandFinishedEvent) {
    commandIsRunning = false
  }
}