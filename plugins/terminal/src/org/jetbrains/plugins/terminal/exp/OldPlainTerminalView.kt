// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import javax.swing.JComponent

/**
 * Once [PlainTerminalView] is feature-rich and stable enough, it will replace [OldPlainTerminalView].
 */
class OldPlainTerminalView(project: Project, settings: JBTerminalSystemSettingsProvider) : TerminalContentView {

  private val widget: ShellTerminalWidget

  override val component: JComponent
    get() = widget.component

  override val preferredFocusableComponent: JComponent
    get() = widget.preferredFocusableComponent

  init {
    widget = ShellTerminalWidget(project, settings, this)
  }

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    widget.asNewWidget().connectToTty(ttyConnector, initialTermSize)
  }

  override fun getTerminalSize(): TermSize? {
    return widget.terminalPanel.terminalSizeFromComponent
  }

  override fun isFocused(): Boolean {
    return widget.terminalPanel.hasFocus()
  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    widget.asNewWidget().addTerminationCallback(onTerminated, parentDisposable)
  }

  override fun dispose() {}
}