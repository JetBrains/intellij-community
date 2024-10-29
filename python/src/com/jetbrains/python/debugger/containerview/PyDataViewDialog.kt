// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.containerview.PyDataView.Companion.isAutoResizeEnabled
import com.jetbrains.python.debugger.containerview.PyDataView.Companion.isColoringEnabled
import com.jetbrains.python.debugger.containerview.PyDataView.Companion.setAutoResizeEnabled
import com.jetbrains.python.debugger.containerview.PyDataView.Companion.setColoringEnabled
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.JPanel

class PyDataViewDialog(private val myProject: Project, value: PyDebugValue) : DialogWrapper(myProject, false) {
  private val mainPanel: JPanel
  private val dataViewerPanel: PyDataViewerPanel
  private var jupyterSuggestionPanel: JPanel? = null

  init {
    isModal = false
    setCancelButtonText(PyBundle.message("debugger.data.view.close"))
    setCrossClosesWindow(true)

    dataViewerPanel = PyDataViewerPanel(myProject, value.frameAccessor)
    dataViewerPanel.apply(value, modifier = false, commandSource = null)
    dataViewerPanel.preferredSize = JBUI.size(TABLE_DEFAULT_WIDTH, TABLE_DEFAULT_HEIGHT)

    mainPanel = JPanel(BorderLayout())

    // If PythonPro installed and enabled.
    val jupyterSuggestionPanel = createJupyterSuggestionPanel()
    this.jupyterSuggestionPanel = jupyterSuggestionPanel
    if (jupyterSuggestionPanel != null) {
      mainPanel.add(jupyterSuggestionPanel, BorderLayout.NORTH)
    }

    mainPanel.add(dataViewerPanel, BorderLayout.CENTER)

    dataViewerPanel.addListener(object : PyDataViewerPanel.Listener {
      override fun onNameChanged(name: @NlsContexts.TabTitle String) {
        title = name
      }
    })

    mainPanel.add(createBottomElements(), BorderLayout.SOUTH)

    title = value.fullName
    init()
  }

  private fun createBottomElements(): JPanel {
    val colored = JBCheckBox(PyBundle.message("debugger.data.view.colored.cells"))
    colored.setSelected(isColoringEnabled(myProject))
    colored.addActionListener(object : ActionListener {
      override fun actionPerformed(e: ActionEvent?) {
        setColoringEnabled(myProject, colored.isSelected)
        dataViewerPanel.isColored = colored.isSelected
      }
    })

    val resize = JBCheckBox(PyBundle.message("debugger.data.view.resize.automatically"))
    resize.setSelected(isAutoResizeEnabled(myProject))
    resize.addActionListener(object : ActionListener {
      override fun actionPerformed(e: ActionEvent?) {
        setAutoResizeEnabled(myProject, resize.isSelected)
        dataViewerPanel.resize(resize.isSelected)
        dataViewerPanel.updateUI()
      }
    })

    return JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(colored)
      add(resize)
    }
  }

  override fun createActions() = arrayOf<Action>(cancelAction)

  override fun createCenterPanel() = mainPanel

  private fun createJupyterSuggestionPanel(): JPanel? {
    if (PlatformUtils.isCommunityEdition()) return null
    if (!isJupyterSuggestionEnabled(myProject)) return null
    val requiredPluginIds = listOf(PluginId.getId("Pythonid"), PluginId.getId("intellij.jupyter"))

    var needToInstallPlugin = false
    val toInstallOrEnable = mutableSetOf<PluginId>().apply {
      requiredPluginIds.forEach { pluginId ->
        val pluginDescriptor = PluginManagerCore.findPlugin(pluginId)
        if (pluginDescriptor == null) {
          add(pluginId)
          needToInstallPlugin = true
        }
        else {
          if (!pluginDescriptor.isEnabled) {
            add(pluginId)
          }
        }
      }
    }

    if (toInstallOrEnable.isEmpty()) return null

    val panel = EditorNotificationPanel()
    panel.text = PyBundle.message("debugger.data.view.reach.view.suggestion")

    fun supposeRestartIfFRequired() {
      val requireRestart = toInstallOrEnable.firstOrNull { PluginManagerCore.getPlugin(it)?.isRequireRestart == true } != null
      if (requireRestart) {
        PluginManagerConfigurable.shutdownOrRestartApp()
      }
    }

    if (needToInstallPlugin) {
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugin.name", "Jupyter")) {
        installAndEnable(myProject, toInstallOrEnable, showDialog = false) {
          hideJupyterSuggestionPanel()
          supposeRestartIfFRequired()
        }
      }
    }
    else {
      panel.createActionLabel(IdeBundle.message("link.enable.required.plugin", "Jupyter")) {
        toInstallOrEnable.forEach { PluginManagerCore.enablePlugin(it) }
        hideJupyterSuggestionPanel()
        supposeRestartIfFRequired()
      }
    }

    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.unknown.feature")) {
      disableJupyterSuggestions(myProject)
      hideJupyterSuggestionPanel()
    }

    return panel
  }

  private fun hideJupyterSuggestionPanel() {
    jupyterSuggestionPanel?.let {
      mainPanel.remove(it)
      mainPanel.revalidate()
      jupyterSuggestionPanel = null
    }
  }

  companion object {
    private const val TABLE_DEFAULT_WIDTH = 700
    private const val TABLE_DEFAULT_HEIGHT = 500

    private const val JUPYTER_SUGGESTION_ENABLED_PROPERTY_KEY = "python.debugger.dataview.jupyter.suggestion.enabled"

    private fun isJupyterSuggestionEnabled(project: Project) = PropertiesComponent.getInstance(project).getBoolean(JUPYTER_SUGGESTION_ENABLED_PROPERTY_KEY, true)

    private fun disableJupyterSuggestions(project: Project) = PropertiesComponent.getInstance(project).setValue(JUPYTER_SUGGESTION_ENABLED_PROPERTY_KEY, false, true)
  }
}