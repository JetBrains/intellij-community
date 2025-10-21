// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptStyle

/**
 * Options related only to the Block terminal.
 */
@ApiStatus.Internal
@Service
@State(name = BlockTerminalOptions.COMPONENT_NAME,
       category = SettingsCategory.TOOLS,
       exportable = true,
       storages = [Storage(value = "terminal.xml")])
class BlockTerminalOptions : PersistentStateComponent<BlockTerminalOptions.State> {
  private val state: State = State()
  private val dispatcher = EventDispatcher.create(BlockTerminalOptionsListener::class.java)

  override fun getState(): State = state

  override fun loadState(newState: State) {
    // Set the values using properties, so listeners will be fired on change.
    // It is important in the case of RemDev when changes are synced from the backend to frontend using this method.
    promptStyle = newState.promptStyle
    showSeparatorsBetweenBlocks = newState.showSeparatorsBetweenBlocks
  }

  override fun noStateLoaded() {
    loadState(State())
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

    internal const val COMPONENT_NAME: String = "BlockTerminalOptions"
  }
}