// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.TerminalContentView
import org.jetbrains.plugins.terminal.block.output.NEW_TERMINAL_OUTPUT_CAPACITY_KB
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalResizeEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSession
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.block.reworked.session.startTerminalSession
import org.jetbrains.plugins.terminal.block.ui.TerminalUi.useTerminalDefaultBackground
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.getCharSize
import org.jetbrains.plugins.terminal.block.ui.stickScrollBarToBottom
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

internal class ReworkedTerminalView(
  private val project: Project,
  private val settings: JBTerminalSystemSettingsProviderBase,
) : TerminalContentView {
  private val coroutineScope = terminalProjectScope(project).childScope("ReworkedTerminalView")
  private val terminalSessionFuture = CompletableFuture<TerminalSession>()

  private val terminationListeners: MutableList<Runnable> = CopyOnWriteArrayList()

  private val outputModel: TerminalOutputModel
  private val sessionModel: TerminalSessionModel
  private val controller: TerminalSessionController
  private val encodingManager: TerminalKeyEncodingManager

  override val component: JComponent
    get() = outputModel.editor.component
  override val preferredFocusableComponent: JComponent
    get() = outputModel.editor.contentComponent

  init {
    Disposer.register(this) {
      // Complete to avoid memory leaks with hanging callbacks. If already completed, nothing will change.
      terminalSessionFuture.complete(null)

      coroutineScope.cancel()
    }

    coroutineScope.coroutineContext.job.invokeOnCompletion {
      for (listener in terminationListeners) {
        try {
          listener.run()
        }
        catch (t: Throwable) {
          thisLogger().error("Unhandled exception in termination listener", t)
        }
      }
    }

    val document = EditorFactory.getInstance().createDocument("")
    val editor = TerminalUiUtils.createOutputEditor(document, project, settings)
    Disposer.register(this) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    editor.settings.isUseSoftWraps = true
    editor.useTerminalDefaultBackground(parentDisposable = this)
    stickScrollBarToBottom(editor.scrollPane.verticalScrollBar)

    val maxOutputLength = AdvancedSettings.getInt(NEW_TERMINAL_OUTPUT_CAPACITY_KB).coerceIn(1, 10 * 1024) * 1024
    outputModel = TerminalOutputModelImpl(editor, maxOutputLength)

    sessionModel = TerminalSessionModelImpl(settings)
    controller = TerminalSessionController(sessionModel, outputModel, settings, coroutineScope.childScope("TerminalSessionController"))
    encodingManager = TerminalKeyEncodingManager(sessionModel, coroutineScope.childScope("TerminalKeyEncodingManager"))

    TerminalCaretPainter(outputModel, coroutineScope.childScope("TerminalCaretPainter"))

    val eventsHandler = TerminalEventsHandlerImpl(sessionModel, outputModel, encodingManager, terminalSessionFuture, settings)
    setupKeyEventDispatcher(outputModel.editor, eventsHandler, disposable = this)
    setupMouseListener(outputModel.editor, sessionModel, settings, eventsHandler, disposable = this)

    component.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        val inputChannel = terminalSessionFuture.getNow(null)?.inputChannel ?: return
        val newSize = getTerminalSize() ?: return
        inputChannel.trySend(TerminalResizeEvent(newSize))
      }
    })
  }

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    val session = startTerminalSession(ttyConnector, initialTermSize, settings, coroutineScope.childScope("TerminalSession"))
    terminalSessionFuture.complete(session)

    controller.handleEvents(session.outputChannel)
  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    TerminalUtil.addItem(terminationListeners, onTerminated, parentDisposable)
  }

  override fun sendCommandToExecute(shellCommand: String) {
    terminalSessionFuture.thenAccept {
      val newLineBytes = encodingManager.getCode(KeyEvent.VK_ENTER, 0)!!
      // TODO: should we always use UTF8?
      val bytes = shellCommand.toByteArray(Charsets.UTF_8) + newLineBytes
      it?.inputChannel?.trySend(TerminalWriteBytesEvent(bytes))
    }
  }

  override fun getTerminalSize(): TermSize? {
    val width = component.width - outputModel.editor.scrollPane.verticalScrollBar.width
    val height = component.height
    val charSize = outputModel.editor.getCharSize()

    return if (width > 0 && height > 0) {
      TerminalUiUtils.calculateTerminalSize(Dimension(width, height), charSize)
    }
    else null
  }

  override fun getTerminalSizeInitializedFuture(): CompletableFuture<*> {
    return TerminalUiUtils.getComponentSizeInitializedFuture(component)
  }

  override fun isFocused(): Boolean {
    return component.hasFocus()
  }

  override fun dispose() {}
}
