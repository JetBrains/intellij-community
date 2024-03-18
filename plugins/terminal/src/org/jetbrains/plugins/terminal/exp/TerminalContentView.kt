// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import javax.swing.JComponent

interface TerminalContentView : Disposable {
  val component: JComponent

  val preferredFocusableComponent: JComponent

  fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize)

  fun getTerminalSize(): TermSize?

  fun isFocused(): Boolean

  @RequiresEdt(generateAssertion = false)
  fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable)

  @RequiresEdt(generateAssertion = false)
  fun sendCommandToExecute(shellCommand: String)
}