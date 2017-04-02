package com.intellij.plugin

import com.intellij.openapi.options.Configurable
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PluginSettingsConfigurable : Configurable {

    override fun isModified(): Boolean {
        return false
    }

    override fun getDisplayName() = "Completion Stats Collector"

    override fun apply() {
    }

    override fun createComponent(): JComponent? {
        val panel = JPanel()
        panel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        return panel
    }

    override fun reset() {
    }

    override fun getHelpTopic(): String? = null

    override fun disposeUIResources() = Unit
    
}