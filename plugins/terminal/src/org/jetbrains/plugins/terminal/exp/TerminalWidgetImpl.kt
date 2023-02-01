// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.terminal.ui.TtyConnectorAccessor
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import javax.swing.JComponent

class TerminalWidgetImpl(private val project: Project,
                         private val terminalSettings: JBTerminalSystemSettingsProviderBase,
                         private val parent: Disposable) : TerminalWidget {
  override val terminalTitle: TerminalTitle = TerminalTitle()

  override val termSize: TermSize?
    get() = if (!blocksContainer.bounds.isEmpty) blocksContainer.getTerminalSize() else null

  override val ttyConnectorAccessor: TtyConnectorAccessor = TtyConnectorAccessor()


  private val session: TerminalSession
  private val blocksContainer: TerminalBlocksContainer

  init {
    session = TerminalSession(project, terminalSettings)
    blocksContainer = TerminalBlocksContainer(project, session, terminalSettings)

    Disposer.register(parent, this)
    Disposer.register(this, session)
    Disposer.register(this, blocksContainer)
  }

  override fun connectToTty(ttyConnector: TtyConnector) {
    session.start(ttyConnector)
    blocksContainer.sizeTerminalToComponent()
  }

  override fun writePlainMessage(message: String) {

  }

  override fun setCursorVisible(visible: Boolean) {

  }

  override fun hasFocus(): Boolean {
    return blocksContainer.isFocused()
  }

  override fun requestFocus() {
    blocksContainer.requestFocus()
  }

  override fun addNotification(notificationComponent: JComponent, disposable: Disposable) {

  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {

  }

  override fun dispose() {

  }

  override fun getComponent(): JComponent = blocksContainer

  override fun getPreferredFocusableComponent(): JComponent = blocksContainer.preferredFocusableComponent
}