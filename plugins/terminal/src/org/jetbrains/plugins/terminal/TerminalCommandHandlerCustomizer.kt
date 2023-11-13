// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.terminal.TerminalCommandHandlerCustomizer.Constants.TERMINAL_COMMAND_HANDLER_TOPIC

internal class TerminalCommandHandlerCustomizer : LocalTerminalCustomizer() {
  override fun getConfigurable(project: Project): UnnamedConfigurable? {
    if (!TerminalShellCommandHandlerHelper.isFeatureEnabled()) {
      return null
    }
    return TerminalCommandHandlerConfigurable(project)
  }

  class TerminalCommandHandlerOptions(val project: Project) {
    var enabled: Boolean
      get() = PropertiesComponent.getInstance().getBoolean(Constants.TERMINAL_CUSTOM_COMMAND_EXECUTION, true)
      set(value) {
        PropertiesComponent.getInstance().setValue(Constants.TERMINAL_CUSTOM_COMMAND_EXECUTION, value, true)
        ApplicationManager.getApplication().messageBus.syncPublisher(TERMINAL_COMMAND_HANDLER_TOPIC).modeChanged()
      }
  }

  interface TerminalCommandHandlerListener {
    fun modeChanged()
  }

  class TerminalCommandHandlerConfigurable(project: Project) :
    BeanConfigurable<TerminalCommandHandlerOptions>(TerminalCommandHandlerOptions(project)) {
    init {
      checkBox(TerminalBundle.message("settings.terminal.smart.command.handling"), { instance!!.enabled }, { instance!!.enabled = it })
    }
  }

  object Constants {
    const val TERMINAL_CUSTOM_COMMAND_EXECUTION = "terminalCustomCommandExecutionTurnOff"

    @JvmStatic
    @Topic.AppLevel
    val TERMINAL_COMMAND_HANDLER_TOPIC = Topic(TerminalCommandHandlerListener::class.java)
  }
}