package com.intellij.settings

import com.intellij.openapi.options.Configurable
import com.intellij.sorting.isMlSortingEnabled
import com.intellij.sorting.setMlSortingEnabled
import com.intellij.ui.components.JBCheckBox
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PluginSettingsConfigurable : Configurable {

    private lateinit var isRerankCompletion: JBCheckBox

    override fun isModified(): Boolean {
        return isMlSortingEnabled() != isRerankCompletion.isSelected
    }

    override fun getDisplayName() = "Completion Stats Collector"

    override fun apply() {
        setMlSortingEnabled(isRerankCompletion.isSelected)
    }

    override fun createComponent(): JComponent? {
        isRerankCompletion = JBCheckBox("Rerank completion", isMlSortingEnabled())
        val panel = JPanel()
        panel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(isRerankCompletion)
        }
        return panel
    }

    override fun reset() {
        isRerankCompletion.isSelected = isMlSortingEnabled()
    }

    override fun getHelpTopic(): String? = null

    override fun disposeUIResources() = Unit
    
}