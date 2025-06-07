// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.icons.AllIcons
import com.intellij.mcpserver.McpServerBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

class PluginSettingsConfigurable : SearchableConfigurable {
    private var settingsPanel: DialogPanel? = null

  override fun getDisplayName(): String = McpServerBundle.message("configurable.name.mcp.plugin")

  override fun createComponent(): JComponent {
    val settings = service<PluginSettings>()
    lateinit var enabledCheckBox: Cell<JBCheckBox>

    val panel = panel {
      row {
        enabledCheckBox = checkBox(McpServerBundle.message("enable.mcp.server")).bindSelected(settings.state::enableMcpServer)
      }
      panel {
        group(McpServerBundle.message("border.title.notifications")) {
          row {
            checkBox(McpServerBundle.message("checkbox.show.node.js.notifications"))
              .bindSelected(settings.state::shouldShowNodeNotification)
          }
          row {
            checkBox(McpServerBundle.message("checkbox.show.claude.notifications"))
              .bindSelected(settings.state::shouldShowClaudeNotification)
          }
          row {
            checkBox(McpServerBundle.message("checkbox.show.claude.settings.notifications"))
              .bindSelected(settings.state::shouldShowClaudeSettingsNotification)
          }
        }
        group(McpServerBundle.message("border.title.terminal.commands")) {
          row {
            checkBox(McpServerBundle.message("checkbox.enable.brave.mode.skip.command.execution.confirmations"))
              .bindSelected(settings.state::enableBraveMode)
          }
          row {
            icon(AllIcons.General.Warning)
            comment(McpServerBundle.message("text.warning.enabling.brave.mode.will.allow.terminal.commands.to.execute.without.confirmation.use.with.caution"))
          }
        }
      }.enabledIf(enabledCheckBox.selected)
    }
        
        settingsPanel = panel
        return panel
    }
    
    override fun isModified(): Boolean {
        return settingsPanel?.isModified() ?: false
    }
    
    override fun apply() {
        settingsPanel?.apply()
    }
    
    override fun reset() {
        settingsPanel?.reset()
    }
    
    override fun disposeUIResources() {
        settingsPanel = null
    }

    override fun getId(): @NonNls String = "com.intellij.mcpserverplugin.settings"
}