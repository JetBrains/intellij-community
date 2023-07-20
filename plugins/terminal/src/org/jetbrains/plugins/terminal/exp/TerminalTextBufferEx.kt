// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalModelListener
import com.jediterm.terminal.model.TerminalTextBuffer
import java.util.concurrent.CopyOnWriteArrayList

class TerminalTextBufferEx(width: Int, height: Int, styleState: StyleState) : TerminalTextBuffer(width, height, styleState) {
  private val listeners: MutableList<TerminalModelListenerEx> = CopyOnWriteArrayList()

  override fun writeString(x: Int, y: Int, str: CharBuffer) {
    super.writeString(x, y, str)
    for (listener in listeners) {
      listener.textWritten(x, y, str.toString())
    }
  }

  override fun addModelListener(listener: TerminalModelListener) {
    super.addModelListener(listener)
    if (listener is TerminalModelListenerEx) {
      listeners.add(listener)
    }
  }

  override fun removeModelListener(listener: TerminalModelListener) {
    super.removeModelListener(listener)
    listeners.remove(listener)
  }
}

interface TerminalModelListenerEx : TerminalModelListener {
  override fun modelChanged() {}

  fun textWritten(x: Int, y: Int, text: String) {}
}