// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.terminal.ui.TtyConnectorAccessor
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.ShellStartupOptions
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JPanel

class TerminalWidgetImpl(private val project: Project,
                         private val terminalSettings: JBTerminalSystemSettingsProviderBase,
                         private val parent: Disposable) : TerminalWidget {
  private val wrapper: Wrapper = Wrapper()

  override val terminalTitle: TerminalTitle = TerminalTitle()

  override val termSize: TermSize?
    get() = controller.getTerminalSize()

  override val ttyConnectorAccessor: TtyConnectorAccessor = TtyConnectorAccessor()


  private val session: TerminalSession = TerminalSession(project, terminalSettings)
  private var controller: TerminalContentController = TerminalPlaceholder()

  init {
    wrapper.setContent(controller.component)
    Disposer.register(parent, this)
    Disposer.register(this, session)
    Disposer.register(this, controller)
  }

  override fun connectToTty(ttyConnector: TtyConnector) {
    ttyConnectorAccessor.ttyConnector = ttyConnector
    session.start(ttyConnector)
  }

  fun setStartupOptions(options: ShellStartupOptions) {
    Disposer.dispose(controller)
    controller = if (options.isBlockShellIntegrationEnabled) {
      TerminalBlocksController(project, session, terminalSettings)
    }
    else PlainTerminalController(project, session, terminalSettings)
    Disposer.register(this, controller)

    wrapper.setContent(controller.component)
    wrapper.revalidate()
    wrapper.repaint()

    IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
  }

  override fun writePlainMessage(message: String) {

  }

  override fun setCursorVisible(visible: Boolean) {

  }

  override fun hasFocus(): Boolean {
    return controller.isFocused()
  }

  override fun requestFocus() {
    controller.component.requestFocus()
  }

  override fun addNotification(notificationComponent: JComponent, disposable: Disposable) {

  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {

  }

  override fun dispose() {

  }

  override fun getComponent(): JComponent = wrapper

  override fun getPreferredFocusableComponent(): JComponent = controller.preferredFocusableComponent

  private class TerminalPlaceholder : TerminalContentController {
    private val panel: JPanel = object : JPanel() {
      override fun getBackground(): Color {
        return UIUtil.getTextFieldBackground()
      }
    }

    override fun getTerminalSize(): TermSize? = null

    override fun isFocused(): Boolean = false

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusableComponent(): JComponent = panel

    override fun dispose() {
    }
  }
}