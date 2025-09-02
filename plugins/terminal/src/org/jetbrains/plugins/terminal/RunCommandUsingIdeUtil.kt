// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic

internal object RunCommandUsingIdeUtil {
  var isEnabled: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(PROPERTY_NAME, DEFAULT_VALUE)
    set(value) {
      PropertiesComponent.getInstance().setValue(PROPERTY_NAME, value, DEFAULT_VALUE)
      ApplicationManager.getApplication().messageBus.syncPublisher(TERMINAL_COMMAND_HANDLER_TOPIC).modeChanged()
    }

  val isVisible: Boolean
    get() = TerminalShellCommandHandlerHelper.isFeatureEnabled()

  interface TerminalCommandHandlerListener {
    fun modeChanged()
  }

  private const val PROPERTY_NAME = "terminalCustomCommandExecutionTurnOff"
  internal const val DEFAULT_VALUE = false

  @JvmField
  @Topic.AppLevel
  internal val TERMINAL_COMMAND_HANDLER_TOPIC = Topic(TerminalCommandHandlerListener::class.java)
}
