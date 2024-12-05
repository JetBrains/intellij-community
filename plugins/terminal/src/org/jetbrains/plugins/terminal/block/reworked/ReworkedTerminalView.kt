// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.block.TerminalContentView
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

internal class ReworkedTerminalView(
  private val project: Project,
  private val settings: JBTerminalSystemSettingsProviderBase,
) : TerminalContentView {
  override val component: JComponent
    get() = TODO("Not yet implemented")
  override val preferredFocusableComponent: JComponent
    get() = TODO("Not yet implemented")

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    TODO("Not yet implemented")
  }

  override fun getTerminalSize(): TermSize? {
    TODO("Not yet implemented")
  }

  override fun getTerminalSizeInitializedFuture(): CompletableFuture<*> {
    TODO("Not yet implemented")
  }

  override fun isFocused(): Boolean {
    TODO("Not yet implemented")
  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    TODO("Not yet implemented")
  }

  override fun sendCommandToExecute(shellCommand: String) {
    TODO("Not yet implemented")
  }

  override fun dispose() {}
}
