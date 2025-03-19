// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.settings.impl

import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.components.*
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.settings.TerminalOsSpecificOptions

@State(name = "TerminalOsSpecificOptions",
       category = SettingsCategory.TOOLS,
       exportable = true,
       storages = [Storage(value = "terminal-os-specific.xml", roamingType = RoamingType.PER_OS)])
internal class TerminalOsSpecificOptionsImpl : TerminalOsSpecificOptions, PersistentStateComponent<TerminalOsSpecificOptionsImpl.State> {
  private var state = State()

  override fun getState(): State {
    return state
  }

  override fun loadState(state: State) {
    this.state = state

    RunOnceUtil.runOnceForApp("TerminalOsSpecificOptions.migration") {
      @Suppress("DEPRECATION")
      val previousCopyOnSelection = TerminalOptionsProvider.instance.state.myCopyOnSelection
      state.copyOnSelection = previousCopyOnSelection
    }
  }

  class State {
    var copyOnSelection: Boolean = SystemInfo.isLinux
  }

  override var copyOnSelection: Boolean
    get() = state.copyOnSelection
    set(value) {
      state.copyOnSelection = value
    }
}