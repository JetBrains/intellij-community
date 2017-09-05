/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.plugin

import com.intellij.completion.enhancer.ContributorsTimeStatistics
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.sorting.SortingTimeStatistics
import com.intellij.stats.completion.experiment.WebServiceStatus
import com.intellij.stats.tracking.IntervalCounter
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import java.awt.event.ActionEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel


class PluginSettingsConfigurableProvider : ConfigurableProvider() {
    override fun createConfigurable() = PluginSettingsConfigurable()
    override fun canCreateConfigurable() = ApplicationManager.getApplication().isInternal
}

class PluginSettingsConfigurable : Configurable {

    private lateinit var manualControlCb: JBCheckBox
    private lateinit var manualSortingCb: JBCheckBox

    override fun isModified(): Boolean {
        val isModifiedStates = mutableListOf<Boolean>()
        isModifiedStates +=
                manualControlCb.isSelected != ManualExperimentControl.isOn
        
        if (manualControlCb.isSelected) {
            isModifiedStates +=
                    manualSortingCb.isSelected != ManualMlSorting.isOn
        }
        
        return isModifiedStates.contains(true)
    }

    override fun getDisplayName() = "Completion Stats Collector"

    override fun apply() {
        ManualExperimentControl.isOn = manualControlCb.isSelected
        if (ManualExperimentControl.isOn) {
            ManualMlSorting.isOn = manualSortingCb.isSelected
        }
    }

    override fun createComponent(): JComponent? {
        val manualControlPanel = manualControlCheckBoxPanel().apply {
            border = IdeBorderFactory.createEmptyBorder(5, 0, 0, 0)
        }
        val timingPanel = timingPanel()
        val autoExperimentPanel = autoExperimentStatusPanel()
        val manualExperimentPanel = manualExperimentPanel()

        val updateStatus: (ActionEvent?) -> Unit = {
            val inManualExperimentMode = manualControlCb.isSelected
            autoExperimentPanel.isVisible = !inManualExperimentMode
            manualExperimentPanel.isVisible = inManualExperimentMode
        }
        
        manualControlCb.addActionListener(updateStatus)
        
        val panel = JPanel()
        panel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(timingPanel)
            add(manualControlPanel)
            add(manualExperimentPanel)
            add(autoExperimentPanel)
            add(contributorsTimingPanel())
        }
        
        updateStatus(null)

        return panel
    }

    private fun manualControlCheckBoxPanel(): JPanel {
        manualControlCb = JBCheckBox("Control experiment manually", ManualExperimentControl.isOn).apply { 
            border = IdeBorderFactory.createEmptyBorder()
        }
        
        val panel = JPanel()
        return panel.apply {
            layout = BoxLayout(panel, BoxLayout.Y_AXIS)    
            add(manualControlCb)
        }
    }

    private fun manualExperimentPanel(): JPanel {
        val action = ActionManager.getInstance().getAction("ToggleManualMlSorting")
        val shortcuts = action.shortcutSet.shortcuts.firstOrNull()?.let { " \"${KeymapUtil.getShortcutText(it)}\"" } ?: ""
        val text = "(can be changed by \"${action.templatePresentation.text}\" action$shortcuts)"
        
        manualSortingCb = JBCheckBox("Enable sorting $text", ManualMlSorting.isOn).apply { 
            border = IdeBorderFactory.createEmptyBorder()
        }
        
        val panel = JPanel()
        return panel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = IdeBorderFactory.createEmptyBorder(5, 0, 0, 0)
            add(manualSortingCb)
        }
    }
    
    private fun timingPanel(): JPanel {
        val panel = JPanel()
        
        val stats = SortingTimeStatistics.getInstance()
        val time: List<String> = stats.state.getTimeDistribution()
        val avgTime: List<String> = stats.state.getAvgTimeByElementsSortedDistribution()
        
        return panel.apply {
            border = IdeBorderFactory.createEmptyBorder(5)
            layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            
            if (time.isNotEmpty()) {
                add(JBLabel("<html><b>Time to Sorts Number Distribution:</b></html>"))
                time.forEach { add(JBLabel(it)) }
            }
            if (avgTime.isNotEmpty()) {
                val label = JBLabel("<html><b>Elements Count to Avg Sorting Time:</b></html>")
                label.border = IdeBorderFactory.createEmptyBorder(5, 0, 0, 0)
                add(label)
                avgTime.forEach { add(JBLabel(it)) }
            }
            if (time.isEmpty() && avgTime.isEmpty()) {
                add(JBLabel("<html><b>No Stats Available</b></html>"))
            }
        }
    }

    private fun contributorsTimingPanel(): JPanel {
        val stats = ContributorsTimeStatistics.getInstance()

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = IdeBorderFactory.createEmptyBorder(5)

            add(JBLabel("<html><b>Contributors Time:</b></html><br/>"))
            stats.languages().forEach {
                add(JBLabel("<html><b>${it.displayName}:</b></html>"))
                add(JBLabel("<html>Initial completion</html>"))
                stats.intervals(it)?.presentation()?.forEach {
                    add(JBLabel(it))
                }
                add(JBLabel("<html>Second completion</html>"))
                stats.secondCompletionIntervals(it)?.presentation()?.forEach {
                    add(JBLabel(it))
                }
            }
        }
    }
    
    private fun autoExperimentStatusPanel(): JPanel {
        val status = WebServiceStatus.getInstance()
        val isExperimentOnCurrentIDE = status.isExperimentOnCurrentIDE()
        val isExperimentGoingOnNow = status.isExperimentGoingOnNow()

        val panel = JPanel()
        return panel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = IdeBorderFactory.createEmptyBorder(5)
            add(JBLabel("<html><b>Is experiment going on now:</b> $isExperimentGoingOnNow</html>"))
            add(JBLabel("<html><b>Is experiment on current IDE:</b> $isExperimentOnCurrentIDE</html>"))
        }
    }

    override fun reset() {
        manualControlCb.isSelected = ManualExperimentControl.isOn
        manualSortingCb.isSelected = ManualMlSorting.isOn
    }

    override fun getHelpTopic(): String? = null

    override fun disposeUIResources() = Unit
    
}


private fun IntervalCounter.presentation(): List<String> {
    return intervals()
            .filter { it.count > 0 }
            .map { ":: Inside ms interval [ ${it.intervalStart}, ${it.intervalEnd} ] happend ${it.count} times" }
}