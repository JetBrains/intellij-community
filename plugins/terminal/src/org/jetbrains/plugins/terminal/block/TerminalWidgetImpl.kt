// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.Disposable
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
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Default implementation of the [TerminalWidget] interface.
 * When the New Terminal is enabled, it attempts to display the Block Terminal
 * if shell integration permits; otherwise, it falls back to the Classic Terminal.
 */
internal class TerminalWidgetImpl(
  private val project: Project,
  private val settings: JBTerminalSystemSettingsProvider,
  parent: Disposable
) : TerminalWidget {
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
    view = if (options.shellIntegration?.commandBlockIntegration != null) {
      val session = BlockTerminalSession(settings, BlockTerminalColorPalette(), options.shellIntegration)
      Disposer.register(this, session)
      BlockTerminalView(project, session, settings, terminalTitle).also {
        installStartupResponsivenessReporter(project, checkNotNull(options.startupMoment), session)
        project.messageBus.syncPublisher(BlockTerminalInitializationListener.TOPIC).modelsInitialized(
          it.promptView.controller.model,
          it.outputView.controller.outputModel
        )
      }
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

    val future = view.getTerminalSizeInitializedFuture()
    TerminalUiUtils.cancelFutureByTimeout(future, 2000, parentDisposable = view)
    return future.thenApply {
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
      init {
        background = TerminalUi.defaultBackground()
      }
    }

    override val preferredFocusableComponent: JComponent = component

    override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
      error("Unexpected method call")
    }

    override fun getTerminalSize(): TermSize? = null

    override fun getTerminalSizeInitializedFuture(): CompletableFuture<*> = CompletableFuture.completedFuture(Unit)

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
