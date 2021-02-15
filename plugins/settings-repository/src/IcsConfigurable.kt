// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository

import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.ui.layout.*
import javax.swing.JCheckBox

internal class IcsConfigurable : ConfigurableBase<IcsConfigurableUi, IcsSettings>("ics", icsMessage("ics.settings"), "reference.settings.ics") {
  override fun getSettings() = if (ApplicationManager.getApplication().isUnitTestMode) IcsSettings() else icsManager.settings

  override fun createUi() = IcsConfigurableUi()
}

internal class IcsConfigurableUi : ConfigurableUi<IcsSettings>, Disposable {
  private val icsManager = if (ApplicationManager.getApplication().isUnitTestMode) IcsManager(PathManager.getConfigDir().resolve("settingsRepository"), this) else org.jetbrains.settingsRepository.icsManager

  private val repositoryListEditor = createRepositoryListEditor(icsManager)
  private val editors = listOf(repositoryListEditor, createReadOnlySourcesEditor())
  private val autoSync = JCheckBox(IdeBundle.message("settings.settings.repository.auto.sync"))
  private val includeHostIntoCommitMessage = JCheckBox(
    IdeBundle.message("settings.settings.repository.include.hostname.into.commit.message"))

  override fun dispose() {
    icsManager.autoSyncManager.enabled = true
  }

  override fun reset(settings: IcsSettings) {
    // do not set in constructor to avoid
    icsManager.autoSyncManager.enabled = false

    autoSync.isSelected = settings.autoSync
    includeHostIntoCommitMessage.isSelected = settings.includeHostIntoCommitMessage

    editors.forEach { it.reset(settings) }
  }

  override fun isModified(settings: IcsSettings): Boolean {
    return autoSync.isSelected != settings.autoSync ||
           includeHostIntoCommitMessage.isSelected != settings.includeHostIntoCommitMessage ||
           editors.any { it.isModified(settings) }
  }

  override fun apply(settings: IcsSettings) {
    settings.autoSync = autoSync.isSelected
    settings.includeHostIntoCommitMessage = includeHostIntoCommitMessage.isSelected

    editors.forEach {
      if (it.isModified(settings)) {
        it.apply(settings)
      }
    }

    saveSettings(settings, icsManager.settingsFile)
  }

  override fun getComponent() = panel {
    repositoryListEditor.buildUi(this)
    row { autoSync(comment = IdeBundle.message("settings.settings.repository.use.vcs.sync.settings.to.sync.when.you.want")) }
    row { includeHostIntoCommitMessage() }
    row { panel(IdeBundle.message("settings.settings.repository.read.only.sources"), editors.get(1).component, false) }
  }
}