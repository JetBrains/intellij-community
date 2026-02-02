// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.settings.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jetbrains.plugins.terminal.settings.TerminalLocalOptions

@State(name = "TerminalLocalOptions",
       category = SettingsCategory.TOOLS,
       exportable = true,
       storages = [Storage(value = "terminal-local.xml", roamingType = RoamingType.LOCAL)])
internal class TerminalLocalOptionsImpl : TerminalLocalOptions, PersistentStateComponent<TerminalLocalOptionsImpl.State> {
  private var state = State()

  override fun getState(): State {
    return state
  }

  override fun loadState(state: State) {
    this.state = state
  }

  class State {
    var shellPath: String? = null
  }

  override var shellPath: String?
    get() = state.shellPath
    set(value) {
      state.shellPath = value
    }
}