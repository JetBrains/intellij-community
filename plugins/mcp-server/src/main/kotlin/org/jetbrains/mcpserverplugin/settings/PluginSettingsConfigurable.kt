package org.jetbrains.mcpserverplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

class PluginSettingsConfigurable : SearchableConfigurable {
    private var settingsPanel: DialogPanel? = null

    override fun getDisplayName(): String = "MCP Plugin"

    override fun createComponent(): JComponent {
        val settings = ApplicationManager.getApplication().getService(PluginSettings::class.java)

        val panel = panel {
            group("Notifications") {
                row {
                    checkBox("Show Node.js notifications")
                        .bindSelected(settings.state::shouldShowNodeNotification)
                }
                row {
                    checkBox("Show Claude notifications")
                        .bindSelected(settings.state::shouldShowClaudeNotification)
                }
                row {
                    checkBox("Show Claude settings notifications")
                        .bindSelected(settings.state::shouldShowClaudeSettingsNotification)
                }
            }
            group("Terminal Commands") {
                row {
                    checkBox("Enable Brave Mode (skip command execution confirmations)")
                        .bindSelected(settings.state::enableBraveMode)
                }
                row {
                    comment("WARNING: Enabling Brave Mode will allow terminal commands to execute without confirmation. Use with caution.")
                }
            }
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

    override fun getId(): @NonNls String = "org.jetbrains.mcpserverplugin.settings"
}