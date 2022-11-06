// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

internal class IcsConfigurable : BoundSearchableConfigurable(icsMessage("ics.settings"), "reference.settings.ics", "ics"),
   Disposable {

  private val icsManager = if (ApplicationManager.getApplication().isUnitTestMode) IcsManager(PathManager.getConfigDir().resolve("settingsRepository"), this) else org.jetbrains.settingsRepository.icsManager
  private val settings = if (ApplicationManager.getApplication().isUnitTestMode) IcsSettings() else icsManager.settings

  private val readOnlySourcesEditor = createReadOnlySourcesEditor()

  override fun dispose() {
    icsManager.autoSyncManager.enabled = true
  }

  override fun reset() {
    super.reset()

    // do not set in constructor to avoid
    icsManager.autoSyncManager.enabled = false
    readOnlySourcesEditor.reset(settings)
  }

  override fun isModified(): Boolean {
    return super.isModified() || readOnlySourcesEditor.isModified(settings)
  }

  override fun apply() {
    super.apply()

    if (readOnlySourcesEditor.isModified(settings)) {
      readOnlySourcesEditor.apply(settings)
    }

    saveSettings(settings, icsManager.settingsFile)
  }

  override fun createPanel(): DialogPanel {
    val repositoryListEditor = createRepositoryListEditor(icsManager)
    return panel {
      row {
        cell(repositoryListEditor)
      }
      row {
        checkBox(IcsBundle.message("settings.auto.sync.checkbox"))
          .comment(IcsBundle.message("settings.auto.sync.comment"))
          .bindSelected(settings::autoSync)
      }
      row {
        checkBox(IcsBundle.message("settings.include.hostname.into.commit.message.checkbox"))
          .bindSelected(settings::includeHostIntoCommitMessage)
      }
      row {
        cell(readOnlySourcesEditor.component)
          .align(Align.FILL)
          .label(IcsBundle.message("settings.read.only.sources.table.header"), LabelPosition.TOP)
      }.resizableRow()
    }.apply {
      registerIntegratedPanel(repositoryListEditor)
    }
  }
}