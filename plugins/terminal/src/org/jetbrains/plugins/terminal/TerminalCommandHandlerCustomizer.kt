// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.Setter

class TerminalCommandHandlerCustomizer : LocalTerminalCustomizer() {
  override fun getConfigurable(project: Project?): UnnamedConfigurable? {
    if (project == null) {
      return super.getConfigurable(project)
    }
    return TerminalCommandHandlerConfigurable(project)
  }

  class TerminalCommandHandlerOptions(val project: Project) {
    var enabled: Boolean
      get() = PropertiesComponent.getInstance(project).getBoolean(TERMINAL_CUSTOM_COMMAND_EXECUTION, true)
      set(value) {
        PropertiesComponent.getInstance(project).setValue(TERMINAL_CUSTOM_COMMAND_EXECUTION, value, true)
      }
  }

  class TerminalCommandHandlerConfigurable(project: Project) :
    BeanConfigurable<TerminalCommandHandlerOptions>(TerminalCommandHandlerOptions(project)), Configurable {
    init {
      checkBox("Smart command handling",
               Getter<Boolean> { instance!!.enabled },
               Setter<Boolean> { instance!!.enabled = it })
    }

    override fun getDisplayName(): String {
      return "TerminalCommandHandler"
    }
  }

  companion object {
    const val TERMINAL_CUSTOM_COMMAND_EXECUTION = "terminalCustomCommandExecutionTurnOff"
  }
}