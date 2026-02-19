// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

@ApiStatus.Internal
interface TerminalContentView : Disposable {
  val component: JComponent

  val preferredFocusableComponent: JComponent

  fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize)

  fun getTerminalSize(): TermSize?

  /**
   * [getTerminalSize] calls can return incorrect values until this future is completed.
   */
  fun getTerminalSizeInitializedFuture(): CompletableFuture<*>

  fun isFocused(): Boolean

  @RequiresEdt(generateAssertion = false)
  fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable)

  @RequiresEdt(generateAssertion = false)
  fun sendCommandToExecute(shellCommand: String)

  /**
   * Returns the **immutable** state of the terminal output text.
   */
  @RequiresEdt(generateAssertion = false)
  fun getText(): CharSequence

  fun getCurrentDirectory(): String? {
    return null
  }
}
