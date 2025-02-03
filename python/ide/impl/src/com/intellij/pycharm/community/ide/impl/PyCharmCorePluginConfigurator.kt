// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.PyCodeInsightSettings
import com.jetbrains.python.console.PyConsoleOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Initialize PyCharm.
 *
 * This class is called **only in PyCharm**.
 * It does not work in plugin
 */
private class PyCharmCorePluginConfigurator : ApplicationInitializedListener {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    val propertyManager = serviceAsync<PropertiesComponent>()
    if (!propertyManager.getBoolean("PyCharm.InitialConfiguration")) {
      propertyManager.setValue("PyCharm.InitialConfiguration", "true")
      EditorSettingsExternalizable.getInstance().setVirtualSpace(false)
    }

    if (!propertyManager.getBoolean("PyCharm.InitialConfiguration.V2")) {
      propertyManager.setValue("PyCharm.InitialConfiguration.V2", true)
      CodeStyle.getDefaultSettings().getCommonSettings(PythonLanguage.getInstance()).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    }

    if (!propertyManager.getBoolean("PyCharm.InitialConfiguration.V3")) {
      propertyManager.setValue("PyCharm.InitialConfiguration.V3", "true")
      (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope().launch {
        while (!LoadingState.COMPONENTS_LOADED.isOccurred) {
          delay(10.milliseconds)
        }

        val fileTypeManager = FileTypeManager.getInstance()
        val ignoredFilesList = fileTypeManager.getIgnoredFilesList()
        writeAction {
          fileTypeManager.setIgnoredFilesList("$ignoredFilesList;*\$py.class")
        }
      }
    }

    if (!propertyManager.getBoolean("PyCharm.InitialConfiguration.V4")) {
      propertyManager.setValue("PyCharm.InitialConfiguration.V4", true)
      PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP = false
    }

    if (!propertyManager.getBoolean("PyCharm.InitialConfiguration.V5")) {
      propertyManager.setValue("PyCharm.InitialConfiguration.V5", true)
      CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT
    }

    if (!propertyManager.getBoolean("PyCharm.InitialConfiguration.V6")) {
      propertyManager.setValue("PyCharm.InitialConfiguration.V6", true)
      CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE = true
    }

    if (!propertyManager.getBoolean("PyCharm.InitialConfiguration.V7")) {
      propertyManager.setValue("PyCharm.InitialConfiguration.V7", true)
    }

    if (!propertyManager.getBoolean("PyCharm.InitialConfiguration.V8")) {
      propertyManager.setValue("PyCharm.InitialConfiguration.V8", true)
      PyConsoleOptions.getInstance(serviceAsync<ProjectManager>().getDefaultProject()).setCommandQueueEnabled(PlatformUtils.isDataSpell())
    }

    serviceAsync<Experiments>().setFeatureEnabled("terminal.shell.command.handling", false)
  }
}
