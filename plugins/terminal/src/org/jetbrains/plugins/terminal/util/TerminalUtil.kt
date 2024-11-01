// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.model.TerminalModelListener
import com.jediterm.terminal.model.TerminalTextBuffer

internal fun TerminalTextBuffer.addModelListener(parentDisposable: Disposable, listener: TerminalModelListener) {
  addModelListener(listener)
  Disposer.register(parentDisposable) {
    removeModelListener(listener)
  }
}
