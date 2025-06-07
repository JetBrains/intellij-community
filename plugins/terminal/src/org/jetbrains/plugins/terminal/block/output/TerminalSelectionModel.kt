// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.Delegates

@ApiStatus.Internal
class TerminalSelectionModel(outputModel: TerminalOutputModel) {
  /** Expected, that last element in the list is primary selection */
  var selectedBlocks: List<CommandBlock> by Delegates.observable(emptyList()) { _, oldValue, newValue ->
    if (newValue != oldValue) {
      listeners.forEach { it.selectionChanged(oldValue, newValue) }
    }
  }

  val primarySelection: CommandBlock?
    get() = selectedBlocks.lastOrNull()

  var hoveredBlock: CommandBlock? = null
    set(value) {
      if (field !== value) {
        listeners.forEach { it.hoverChanged(field, value) }
      }
      field = value
    }

  private val listeners: MutableList<TerminalSelectionListener> = CopyOnWriteArrayList()

  init {
    outputModel.addListener(object : TerminalOutputModelListener {
      override fun blockRemoved(block: CommandBlock) {
        selectedBlocks -= block
        if (hoveredBlock === block) {
          hoveredBlock = null
        }
      }
    })
  }

  fun addListener(listener: TerminalSelectionListener, disposable: Disposable? = null) {
    listeners.add(listener)
    if (disposable != null) {
      Disposer.register(disposable) { listeners.remove(listener) }
    }
  }

  interface TerminalSelectionListener {
    fun selectionChanged(oldSelection: List<CommandBlock>, newSelection: List<CommandBlock>) {}

    fun hoverChanged(oldHovered: CommandBlock?, newHovered: CommandBlock?) {}
  }
}
