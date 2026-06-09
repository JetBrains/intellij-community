// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service
@State(name = "MermaidSettings", storages = [(Storage("mermaid.xml"))])
class MermaidSettings : SimplePersistentStateComponent<MermaidSettingsState>(MermaidSettingsState()) {
  companion object {
    fun getInstance(): MermaidSettings = service()
  }

  var theme: MermaidSettingsState.Theme
    get() = state.theme
    set(value) {
      state.theme = value
    }
}
