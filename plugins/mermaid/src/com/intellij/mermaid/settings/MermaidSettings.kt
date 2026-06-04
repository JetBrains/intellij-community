package com.intellij.mermaid.settings

import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "MermaidSettings", storages = [(Storage("mermaid.xml"))])
class MermaidSettings : SimplePersistentStateComponent<MermaidSettingsState>(MermaidSettingsState()) {
  companion object {
    fun getInstance(): MermaidSettings {
      return service()
    }
  }

  var theme
    get() = state.theme
    set(value) {
      state.theme = value
    }
}
