// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.bindApplicationTitle
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils.getComponentSizeInitializedFuture
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

/**
 * Once [PlainTerminalView] is feature-rich and stable enough, it will replace [OldPlainTerminalView].
 */
internal class OldPlainTerminalView(project: Project,
                                    settings: JBTerminalSystemSettingsProvider,
                                    terminalTitle: TerminalTitle) : TerminalContentView {

  private val widget: ShellTerminalWidget

  override val component: JComponent
    get() = widget.component

  override val preferredFocusableComponent: JComponent
    get() = widget.preferredFocusableComponent

  init {
    widget = ShellTerminalWidget(project, settings, this)
    terminalTitle.bindApplicationTitle(widget.terminal, this)
  }

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    widget.asNewWidget().connectToTty(ttyConnector, initialTermSize)
  }

  override fun getTerminalSize(): TermSize? {
    return widget.terminalPanel.terminalSizeFromComponent
  }

  override fun getTerminalSizeInitializedFuture(): CompletableFuture<*> {
    return getComponentSizeInitializedFuture(component)
  }

  override fun isFocused(): Boolean {
    return widget.terminalPanel.hasFocus()
  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    widget.asNewWidget().addTerminationCallback(onTerminated, parentDisposable)
  }

  override fun sendCommandToExecute(shellCommand: String) {
    widget.executeCommand(shellCommand)
  }

  override fun getText(): CharSequence {
    return widget.text
  }

  override fun getCurrentDirectory(): String? {
    return widget.currentDirectory
  }

  override fun dispose() {}
}
