// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.StateStorageChooserEx
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.Storage

@Service(Service.Level.PROJECT)
@State(name = "MarkdownSettings", storages = [Storage(MARKDOWN_SETTINGS_FILE_NAME)], allowLoadInTests = true)
internal class LegacyMarkdownSettingsV1 : PersistentStateComponent<MarkdownSettingsState>, StateStorageChooserEx {
  var loadedState: MarkdownSettingsState? = null
    private set

  override fun getState(): MarkdownSettingsState? = null

  override fun getResolution(storage: Storage, operation: StateStorageOperation): StateStorageChooserEx.Resolution {
    return if (operation == StateStorageOperation.READ) StateStorageChooserEx.Resolution.DO else StateStorageChooserEx.Resolution.SKIP
  }

  override fun loadState(state: MarkdownSettingsState) {
    loadedState = state
  }
}
