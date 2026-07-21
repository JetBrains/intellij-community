// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.suggestions

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.debugger.installPythonDapPlugin
import com.jetbrains.python.debugger.isPythonDapPluginInstalledAndEnabled

private const val PYTHON_DAP_PLUGIN_ID = "intellij.python.dap.plugin"
private const val PYTHON_DAP_SUGGESTION_DISMISSED_KEY = "python.dap.suggestion.dismissed"

internal class PythonDapPluginSuggestionProvider : PluginSuggestionProvider {
  override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
    if (!file.isPythonOrNotebook()) return null
    if (isPyCharm()) return null
    if (isDismissed()) return null
    if (isPythonDapPluginInstalledAndEnabled()) return null

    return PythonDapPluginSuggestion(project)
  }

  private fun isPyCharm(): Boolean {
    val productCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode
    return productCode == "PY" || productCode == "PC"
  }

  private fun isDismissed(): Boolean =
    PropertiesComponent.getInstance().isTrueValue(PYTHON_DAP_SUGGESTION_DISMISSED_KEY)

  private fun VirtualFile.isPythonOrNotebook(): Boolean =
    fileType == PythonFileType.INSTANCE || extension == "ipynb"

  private class PythonDapPluginSuggestion(
    private val project: Project,
  ) : PluginSuggestion {
    override val pluginIds: List<String> = listOf(PYTHON_DAP_PLUGIN_ID)

    override fun apply(fileEditor: FileEditor): EditorNotificationPanel {
      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info)
      panel.text = PyBundle.message("advertiser.python.dap.plugin")

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugin.name", "Python DAP Debugger")) {
        FUSEventSource.EDITOR.logInstallPlugins(pluginIds, project)
        installPythonDapPlugin(project) {
          EditorNotifications.getInstance(project).updateAllNotifications()
        }
      }

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
        FUSEventSource.EDITOR.logIgnoreExtension(project)
        PropertiesComponent.getInstance().setValue(PYTHON_DAP_SUGGESTION_DISMISSED_KEY, true)
        EditorNotifications.getInstance(project).updateAllNotifications()
      }

      return panel
    }
  }
}
