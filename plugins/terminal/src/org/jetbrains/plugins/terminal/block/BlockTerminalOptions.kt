// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptStyle
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Options related only to the Block terminal.
 */
@Service
@State(name = "BlockTerminalOptions", storages = [Storage(value = "terminal.xml", roamingType = RoamingType.DISABLED)])
internal class BlockTerminalOptions : PersistentStateComponent<BlockTerminalOptions.State> {
  private var state: State = State()
  private val listeners: MutableList<() -> Unit> = CopyOnWriteArrayList()

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }

  var promptStyle: TerminalPromptStyle
    get() = state.promptStyle
    set(value) {
      if (state.promptStyle != value) {
        state.promptStyle = value
        fireSettingsChanged()
      }
    }

  /** [listener] is invoked when any option is changed */
  fun addListener(parentDisposable: Disposable, listener: () -> Unit) {
    TerminalUtil.addItem(listeners, listener, parentDisposable)
  }

  private fun fireSettingsChanged() {
    for (listener in listeners) {
      listener()
    }
  }

  class State {
    // Default is double line, but we should take into account the migration from previously existing setting.
    // So, if 'useShellPrompt' was set to true, we need to follow it.
    @Suppress("DEPRECATION")
    var promptStyle: TerminalPromptStyle = if (TerminalOptionsProvider.instance.useShellPrompt) TerminalPromptStyle.SHELL else TerminalPromptStyle.DOUBLE_LINE
  }

  companion object {
    @JvmStatic
    fun getInstance(): BlockTerminalOptions = service()
  }
}