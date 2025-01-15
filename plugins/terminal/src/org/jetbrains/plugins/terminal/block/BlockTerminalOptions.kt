// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptStyle

/**
 * Options related only to the Block terminal.
 */
@Service
@State(name = "BlockTerminalOptions", storages = [Storage(value = "terminal.xml", roamingType = RoamingType.DISABLED)])
internal class BlockTerminalOptions : PersistentStateComponent<BlockTerminalOptions.State> {
  private var state: State = State()
  private val dispatcher = EventDispatcher.create(BlockTerminalOptionsListener::class.java)

  override fun getState(): State = state

  @Suppress("DEPRECATION")
  override fun loadState(state: State) {
    this.state = state

    // Migrate the value from the previously existing setting if it was non default.
    // So, if 'useShellPrompt' was set to true, we need to follow it.
    val options = TerminalOptionsProvider.instance
    if (options.useShellPrompt) {
      // Access state directly, no need to fire settings changed event now.
      this.state.promptStyle = TerminalPromptStyle.SHELL
      options.useShellPrompt = false
    }
  }

  var promptStyle: TerminalPromptStyle
    get() = state.promptStyle
    set(value) {
      if (state.promptStyle != value) {
        state.promptStyle = value
        dispatcher.multicaster.promptStyleChanged(value)
      }
    }

  var showSeparatorsBetweenBlocks: Boolean
    get() = state.showSeparatorsBetweenBlocks
    set(value) {
      if (state.showSeparatorsBetweenBlocks != value) {
        state.showSeparatorsBetweenBlocks = value
        dispatcher.multicaster.showSeparatorsBetweenBlocksChanged(value)
      }
    }

  fun addListener(parentDisposable: Disposable, listener: BlockTerminalOptionsListener) {
    dispatcher.addListener(listener, parentDisposable)
  }

  class State {
    var promptStyle: TerminalPromptStyle = TerminalPromptStyle.DOUBLE_LINE
    var showSeparatorsBetweenBlocks: Boolean = true
  }

  companion object {
    @JvmStatic
    fun getInstance(): BlockTerminalOptions = service()
  }
}