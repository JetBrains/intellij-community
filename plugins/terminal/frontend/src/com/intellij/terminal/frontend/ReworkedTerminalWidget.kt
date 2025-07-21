package com.intellij.terminal.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.session.TerminalSession
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.terminal.ui.TtyConnectorAccessor
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

internal class ReworkedTerminalWidget(
  private val project: Project,
  settings: JBTerminalSystemSettingsProvider,
  startupFusInfo: TerminalStartupFusInfo?,
  parentDisposable: Disposable,
) : TerminalWidget {
  private val sessionFuture = CompletableFuture<TerminalSession>()
  private val view = ReworkedTerminalView(project, settings, sessionFuture, startupFusInfo)

  override val terminalTitle: TerminalTitle = TerminalTitle()

  override val termSize: TermSize?
    get() = view.getTerminalSize()

  override var shellCommand: List<String>? = null

  override val session: TerminalSession?
    get() = sessionFuture.getNow(null)

  init {
    Disposer.register(parentDisposable, this)
    Disposer.register(this, view)
  }

  override fun connectToSession(session: TerminalSession) {
    sessionFuture.complete(session)
  }

  override fun getTerminalSizeInitializedFuture(): CompletableFuture<TermSize> {
    return view.getTerminalSizeInitializedFuture().thenApply { termSize }
  }

  override fun getComponent(): JComponent {
    return view.component
  }

  override fun getPreferredFocusableComponent(): JComponent {
    return view.preferredFocusableComponent
  }

  override fun hasFocus(): Boolean {
    return view.isFocused()
  }

  override fun requestFocus() {
    IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
  }

  override fun sendCommandToExecute(shellCommand: String) {
    view.sendCommandToExecute(shellCommand)
  }

  override fun getText(): CharSequence {
    return view.getText()
  }

  override fun isCommandRunning(): Boolean {
    val session = session ?: return false
    return runWithModalProgressBlocking(project, "") {
      session.hasRunningCommands()
    }
  }

  override fun getCurrentDirectory(): String? {
    return view.getCurrentDirectory()
  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    view.addTerminationCallback(onTerminated, parentDisposable)
  }

  override fun addNotification(notificationComponent: JComponent, disposable: Disposable) {
    view.setTopComponent(notificationComponent, disposable)
  }

  override fun writePlainMessage(message: @Nls String) {
    // TODO: implement
  }

  override fun setCursorVisible(visible: Boolean) {
    // TODO: implement
  }

  override fun dispose() {}

  //-------------------------Not supported part ------------------------------------------------------------

  override val ttyConnectorAccessor: TtyConnectorAccessor = TtyConnectorAccessor()

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    error("connectToTty is not supported by ReworkedTerminalWidget, use connectToSession instead")
  }
}