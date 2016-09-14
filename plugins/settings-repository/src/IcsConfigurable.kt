/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.settingsRepository

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.ui.layout.*
import javax.swing.JCheckBox

internal class IcsConfigurable : ConfigurableBase<IcsConfigurableUi, IcsSettings>("ics", icsMessage("ics.settings"), "reference.settings.ics") {
  override fun getSettings() = icsManager.settings

  override fun createUi() = IcsConfigurableUi()
}

internal class IcsConfigurableUi : ConfigurableUi<IcsSettings>, Disposable {
  private val editors = listOf(createRepositoryListEditor(), createReadOnlySourcesEditor())
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

    editors.forEach { it.apply(settings) }

    saveSettings(settings, icsManager.settingsFile)
  }

  override fun getComponent() = verticalPanel {
    editors.get(0).component()
    autoSync()
    hint("Use VCS -> Sync Settings to sync when you want")
    panel("Read-only Sources", editors.get(1).component)
  }
}