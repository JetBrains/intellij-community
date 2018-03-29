// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.ui.layout.*
import java.nio.file.Paths
import javax.swing.JCheckBox

internal class IcsConfigurable : ConfigurableBase<IcsConfigurableUi, IcsSettings>("ics", icsMessage("ics.settings"), "reference.settings.ics") {
  override fun getSettings() = if (ApplicationManager.getApplication().isUnitTestMode) IcsSettings() else icsManager.settings

  override fun createUi() = IcsConfigurableUi()
}

internal class IcsConfigurableUi : ConfigurableUi<IcsSettings>, Disposable {
  private val icsManager = if (ApplicationManager.getApplication().isUnitTestMode) IcsManager(Paths.get(PathManager.getConfigPath()).resolve("settingsRepository")) else org.jetbrains.settingsRepository.icsManager

  private val repositoryListEditor = createRepositoryListEditor(icsManager)
  private val editors = listOf(repositoryListEditor, createReadOnlySourcesEditor())
  private val autoSync = JCheckBox("Auto Sync")

  override fun dispose() {
    icsManager.autoSyncManager.enabled = true
  }

  override fun reset(settings: IcsSettings) {
    // do not set in constructor to avoid
    icsManager.autoSyncManager.enabled = false

    autoSync.isSelected = settings.autoSync

    editors.forEach { it.reset(settings) }
  }

  override fun isModified(settings: IcsSettings) = autoSync.isSelected != settings.autoSync || editors.any { it.isModified(settings) }

  override fun apply(settings: IcsSettings) {
    settings.autoSync = autoSync.isSelected

    editors.forEach {
      if (it.isModified(settings)) {
        it.apply(settings)
      }
    }

    saveSettings(settings, icsManager.settingsFile)
  }

  override fun getComponent() = panel {
    repositoryListEditor.buildUi(this)
    row { autoSync(comment = "Use VCS -> Sync Settings to sync when you want") }
    row { panel("Read-only Sources", editors.get(1).component) }
  }
}