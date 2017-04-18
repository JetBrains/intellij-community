package com.intellij.plugin

import com.intellij.openapi.options.Configurable
import com.intellij.sorting.SortingTimeStatistics
import com.intellij.ui.components.JBLabel
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
            val field = JBLabel(getHtmlText())
            add(field)
        }
        return panel
    }
    
    private fun getHtmlText(): String {
        val stats = SortingTimeStatistics.getInstance()
        val time = stats.state.getTimeDistribution()
        val avgTime = stats.state.getAvgTimeByElementsSortedDistribution()

        if (time.isEmpty() && avgTime.isEmpty()) {
            return "No stats available"
        }
        
        return "<html>Time to Sorts Number Distribution:<br>" + time.joinToString("<br>") +
                "<br><br>Elements Count to Avg Sorting Time<br>" + avgTime.joinToString("<br>") + "</html>"
    }

    override fun reset() {
    }

    override fun getHelpTopic(): String? = null

    override fun disposeUIResources() = Unit
    
}