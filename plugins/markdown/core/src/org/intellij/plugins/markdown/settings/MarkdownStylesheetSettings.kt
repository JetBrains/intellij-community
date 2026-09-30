// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "MarkdownStylesheetSettings", storages = [Storage(MARKDOWN_SETTINGS_FILE_NAME)], allowLoadInTests = true)
internal class MarkdownStylesheetSettings(private val project: Project) : SimplePersistentStateComponent<MarkdownStylesheetSettings.State>(State()) {
  class State : BaseState() {
    var useCustomStylesheetPath: Boolean? by property(null) { it == null }
    var customStylesheetPath: String? by property(null) { it == null }
  }

  private val legacyState: MarkdownSettingsState?
    get() = if (state.useCustomStylesheetPath == null && state.customStylesheetPath == null) {
      project.service<LegacyMarkdownSettingsV1>().loadedState
    }
    else null

  var useCustomStylesheetPath: Boolean
    get() = state.useCustomStylesheetPath ?: legacyState?.useCustomStylesheetPath ?: false
    set(value) {
      state.customStylesheetPath = customStylesheetPath.orEmpty()
      state.useCustomStylesheetPath = value
    }

  var customStylesheetPath: String?
    get() = (state.customStylesheetPath ?: legacyState?.customStylesheetPath)?.takeIf { it.isNotEmpty() }
    set(value) {
      state.useCustomStylesheetPath = useCustomStylesheetPath
      state.customStylesheetPath = value.orEmpty()
    }

  override fun noStateLoaded() {
    loadState(State())
  }

  companion object {
    fun getInstance(project: Project): MarkdownStylesheetSettings = project.service()
  }
}
