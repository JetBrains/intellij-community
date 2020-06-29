// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.Setter
import com.intellij.util.messages.Topic
import java.util.*

class TerminalCommandHandlerCustomizer : LocalTerminalCustomizer() {
  override fun getConfigurable(project: Project?): UnnamedConfigurable? {
    if (project == null || !TerminalShellCommandHandlerHelper.isFeatureEnabled()) {
      return null
    }
    return TerminalCommandHandlerConfigurable(project)
  }

  class TerminalCommandHandlerOptions(val project: Project) {
    var enabled: Boolean
      get() = PropertiesComponent.getInstance().getBoolean(TERMINAL_CUSTOM_COMMAND_EXECUTION, true)
      set(value) {
        PropertiesComponent.getInstance().setValue(TERMINAL_CUSTOM_COMMAND_EXECUTION, value, true)
        ApplicationManager.getApplication().messageBus.syncPublisher(TERMINAL_COMMAND_HANDLER_TOPIC).modeChanged()
      }
  }

  interface TerminalCommandHandlerListener {
    fun modeChanged()
  }

  class TerminalCommandHandlerConfigurable(project: Project) :
    BeanConfigurable<TerminalCommandHandlerOptions>(TerminalCommandHandlerOptions(project)), Configurable {
    init {
      checkBox(TerminalBundle.message("settings.terminal.smart.command.handling"),
               Getter { instance!!.enabled },
               Setter { instance!!.enabled = it })
    }

    override fun getDisplayName(): String {
      return "TerminalCommandHandler"
    }
  }

  companion object {
    const val TERMINAL_CUSTOM_COMMAND_EXECUTION = "terminalCustomCommandExecutionTurnOff"

    @Topic.AppLevel
    val TERMINAL_COMMAND_HANDLER_TOPIC = Topic(TerminalCommandHandlerListener::class.java)
  }
}