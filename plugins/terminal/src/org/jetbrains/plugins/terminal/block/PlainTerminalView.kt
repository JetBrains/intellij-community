// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils.getComponentSizeInitializedFuture
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

/**
 * Once it's feature-rich and stable enough, it will replace [OldPlainTerminalView].
 */
@Suppress("unused")
internal class PlainTerminalView(
  project: Project,
  private val session: BlockTerminalSession,
  settings: JBTerminalSystemSettingsProviderBase
) : TerminalContentView {
  override val component: JComponent
    get() = view.component
  override val preferredFocusableComponent: JComponent
    get() = view.preferredFocusableComponent

  private val view: SimpleTerminalView

  init {
    view = SimpleTerminalView(project, settings, session)
    view.component.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        val newSize = getTerminalSize() ?: return
        session.postResize(newSize)
      }
    })

    Disposer.register(this, view)
  }

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    session.controller.resize(initialTermSize, RequestOrigin.User)
    session.start(ttyConnector)
  }

  // return preferred size of the terminal calculated from the component size
  override fun getTerminalSize(): TermSize? {
    if (view.component.bounds.isEmpty) return null
    val contentSize = Dimension(view.terminalWidth, view.component.height)
    return TerminalUiUtils.calculateTerminalSize(contentSize, view.charSize)
  }

  override fun getTerminalSizeInitializedFuture(): CompletableFuture<*> {
    return getComponentSizeInitializedFuture(component)
  }

  override fun isFocused(): Boolean {
    return view.isFocused()
  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    session.addTerminationCallback(onTerminated, parentDisposable)
  }

  override fun sendCommandToExecute(shellCommand: String) {
    session.commandExecutionManager.sendCommandToExecute(shellCommand)
  }

  override fun getText(): CharSequence {
    return view.getText()
  }

  override fun dispose() {}
}
