// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.PlatformUtils
import com.jetbrains.python.codeInsight.PyCodeInsightSettings
import com.jetbrains.python.console.PyConsoleOptions

/**
 * Initialize PyCharm.
 *
 * This class is called **only in PyCharm**.
 * It does not work in plugin
 */
internal class PyCharmCorePluginConfigurator : ApplicationInitializedListener {
  override fun componentsInitialized() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return
    }
    val propertiesComponent = PropertiesComponent.getInstance()
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration", "true")
      EditorSettingsExternalizable.getInstance().setVirtualSpace(false)
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V2")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V2", true)
      val settings = CodeStyle.getDefaultSettings()
      settings.getCommonSettings(PythonLanguage.getInstance()).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V3")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V3", "true")
      val ignoredFilesList = FileTypeManager.getInstance().getIgnoredFilesList()
      ApplicationManager.getApplication().invokeLater(Runnable {
        ApplicationManager.getApplication().runWriteAction(
          Runnable { FileTypeManager.getInstance().setIgnoredFilesList("$ignoredFilesList;*\$py.class") })
      })
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V4")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V4", true)
      PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP = false
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V5")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V5", true)
      CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V6")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V6", true)
      CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE = true
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V7")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V7", true)
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V8")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V8", true)
      PyConsoleOptions.getInstance(ProjectManager.getInstance().getDefaultProject()).setCommandQueueEnabled(PlatformUtils.isDataSpell())
    }
    Experiments.getInstance().setFeatureEnabled("terminal.shell.command.handling", false)
  }
}
