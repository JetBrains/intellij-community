// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync

@Service(Service.Level.APP)
@State(name = "MarkdownApplicationSettings",
       category = SettingsCategory.CODE,
       storages = [(Storage("markdown.xml"))])
class MarkdownApplicationSettings: SimplePersistentStateComponent<MarkdownApplicationSettings.State>(State()) {
  class State: BaseState() {
    var alignTableCellsVisually: Boolean by property(true)
    var enableLivePreview: Boolean by property(true)
  }

  var alignTableCellsVisually: Boolean
    get() = state.alignTableCellsVisually
    set(value) { state.alignTableCellsVisually = value }

  var enableLivePreview: Boolean
    get() = state.enableLivePreview
    set(value) { state.enableLivePreview = value }

  override fun noStateLoaded() {
    loadState(State())
  }

  companion object {
    @JvmStatic
    fun getInstance(): MarkdownApplicationSettings = service()

    suspend fun getInstanceAsync(): MarkdownApplicationSettings = serviceAsync()
  }
}
