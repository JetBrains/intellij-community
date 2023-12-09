// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.terminal.ui.TtyConnectorAccessor
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.exp.ui.BlockTerminalColorPalette
import java.awt.Color
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JPanel

class TerminalWidgetImpl(private val project: Project,
                         private val settings: JBTerminalSystemSettingsProvider,
                         parent: Disposable) : TerminalWidget {
  private val wrapper: Wrapper = Wrapper()

  override val terminalTitle: TerminalTitle = TerminalTitle()

  override val termSize: TermSize?
    get() = view.getTerminalSize()

  override val ttyConnectorAccessor: TtyConnectorAccessor = TtyConnectorAccessor()

  override var shellCommand: List<String>? = null

  @Volatile
  private var view: TerminalContentView = TerminalPlaceholder()

  init {
    wrapper.setContent(view.component)
    Disposer.register(parent, this)
    Disposer.register(this, view)
  }

  @RequiresEdt(generateAssertion = false)
  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    view.connectToTty(ttyConnector, initialTermSize)
    ttyConnectorAccessor.ttyConnector = ttyConnector
  }

  @RequiresEdt(generateAssertion = false)
  fun initialize(options: ShellStartupOptions): CompletableFuture<TermSize> {
    val oldView = view
    view = if (options.shellIntegration?.withCommandBlocks == true) {
      val colorPalette = BlockTerminalColorPalette(EditorColorsManager.getInstance().getGlobalScheme())
      val session = BlockTerminalSession(settings, colorPalette, options.shellIntegration)
      Disposer.register(this, session)
      BlockTerminalView(project, session, settings, terminalTitle)
    }
    else {
      OldPlainTerminalView(project, settings, terminalTitle)
    }
    if (oldView is TerminalPlaceholder) {
      oldView.moveTerminationCallbacksTo(view)
      oldView.executePostponedShellCommands(view)
    }
    Disposer.dispose(oldView)
    Disposer.register(this, view)

    val component = view.component
    wrapper.setContent(component)
    requestFocus()

    return TerminalUiUtils.awaitComponentLayout(component, view).thenApply {
      view.getTerminalSize()
    }
  }

  override fun writePlainMessage(message: String) {

  }

  override fun setCursorVisible(visible: Boolean) {

  }

  override fun hasFocus(): Boolean {
    return view.isFocused()
  }

  override fun requestFocus() {
    IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
  }

  override fun addNotification(notificationComponent: JComponent, disposable: Disposable) {

  }

  override fun sendCommandToExecute(shellCommand: String) {
    view.sendCommandToExecute(shellCommand)
  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    view.addTerminationCallback(onTerminated, parentDisposable)
  }

  override fun dispose() {}

  override fun getComponent(): JComponent = wrapper

  override fun getPreferredFocusableComponent(): JComponent = view.preferredFocusableComponent

  private class TerminalPlaceholder : TerminalContentView {

    private val postponedTerminationCallbackInfos: MutableList<Pair<Runnable, Disposable>> = CopyOnWriteArrayList()
    private val postponedShellCommands: MutableList<String> = CopyOnWriteArrayList()

    override val component: JComponent = object : JPanel() {
      override fun getBackground(): Color {
        return TerminalUi.terminalBackground
      }
    }

    override val preferredFocusableComponent: JComponent = component

    override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
      error("Unexpected method call")
    }

    override fun getTerminalSize(): TermSize? = null

    override fun isFocused(): Boolean = false

    override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
      postponedTerminationCallbackInfos.add(Pair(onTerminated, parentDisposable))
    }

    override fun sendCommandToExecute(shellCommand: String) {
      postponedShellCommands.add(shellCommand)
    }

    fun moveTerminationCallbacksTo(destView: TerminalContentView) {
      for (info in postponedTerminationCallbackInfos) {
        destView.addTerminationCallback(info.first, info.second)
      }
      postponedTerminationCallbackInfos.clear()
    }

    fun executePostponedShellCommands(destView: TerminalContentView) {
      for (shellCommand in postponedShellCommands) {
        destView.sendCommandToExecute(shellCommand)
      }
      postponedShellCommands.clear()
    }

    override fun dispose() {
      postponedTerminationCallbackInfos.clear()
    }
  }
}