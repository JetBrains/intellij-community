// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.terminal.exp.TerminalOutputModel.TerminalOutputListener
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.Delegates

class TerminalSelectionModel(private val outputModel: TerminalOutputModel) {
  /** Expected, that last element in the list is primary selection */
  var selectedBlocks: List<CommandBlock> by Delegates.observable(emptyList()) { _, oldValue, newValue ->
    if (newValue != oldValue) fireSelectionChanged(oldValue, newValue)
  }

  val primarySelection: CommandBlock?
    get() = selectedBlocks.lastOrNull()

  private val listeners: MutableList<TerminalSelectionListener> = CopyOnWriteArrayList()

  init {
    outputModel.addListener(object : TerminalOutputListener {
      override fun blockRemoved(block: CommandBlock) {
        selectedBlocks -= block
      }
    })
  }

  fun addListener(listener: TerminalSelectionListener, disposable: Disposable? = null) {
    listeners.add(listener)
    if (disposable != null) {
      Disposer.register(disposable) { listeners.remove(listener) }
    }
  }

  private fun fireSelectionChanged(oldSelection: List<CommandBlock>, newSelection: List<CommandBlock>) {
    listeners.forEach { it.selectionChanged(oldSelection, newSelection) }
    for (block in oldSelection) {
      if (!newSelection.contains(block)) {
        outputModel.removeBlockState(block, SelectedBlockDecorationState.NAME)
      }
    }
    for (block in newSelection) {
      outputModel.addBlockState(block, SelectedBlockDecorationState())
    }
  }

  interface TerminalSelectionListener {
    fun selectionChanged(oldSelection: List<CommandBlock>, newSelection: List<CommandBlock>) {}
  }
}