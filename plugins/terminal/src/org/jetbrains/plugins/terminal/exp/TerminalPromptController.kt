// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.IS_PROMPT_EDITOR_KEY
import org.jetbrains.plugins.terminal.exp.completion.IJShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.completion.ShellCommandExecutor
import org.jetbrains.plugins.terminal.exp.completion.ShellCommandExecutorImpl
import org.jetbrains.plugins.terminal.exp.history.CommandHistoryManager
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.Delegates

class TerminalPromptController(
  private val editor: EditorEx,
  session: BlockTerminalSession,
  private val commandExecutor: TerminalCommandExecutor
) {
  private val commandHistoryManager: CommandHistoryManager
  private val promptModel: TerminalPromptModel = TerminalPromptModel(session)
  private val listeners: MutableList<PromptStateListener> = CopyOnWriteArrayList()

  val commandHistory: List<String>
    get() = commandHistoryManager.history

  // should be accessed in EDT
  var promptIsVisible: Boolean by Delegates.observable(true) { _, oldValue, newValue ->
    if (newValue != oldValue) listeners.forEach { it.promptVisibilityChanged(newValue) }
  }

  val promptRenderingInfo: PromptRenderingInfo
    get() = promptModel.renderingInfo

  val commandText: String
    get() = editor.document.text

  init {
    editor.putUserData(IS_PROMPT_EDITOR_KEY, true)
    editor.putUserData(BlockTerminalSession.KEY, session)

    val shellCommandExecutor = ShellCommandExecutorImpl(session)
    editor.putUserData(ShellCommandExecutor.KEY, shellCommandExecutor)
    val runtimeDataProvider = IJShellRuntimeDataProvider(session, shellCommandExecutor)
    editor.putUserData(IJShellRuntimeDataProvider.KEY, runtimeDataProvider)

    commandHistoryManager = CommandHistoryManager(session)

    promptModel.addListener(object : TerminalPromptStateListener {
      override fun promptStateUpdated(renderingInfo: PromptRenderingInfo) {
        listeners.forEach { it.promptContentUpdated(renderingInfo) }
      }
    }, disposable = session)
  }

  fun addListener(listener: PromptStateListener) {
    listeners.add(listener)
  }

  @RequiresEdt
  fun reset() {
    runWriteAction {
      editor.document.setText("")
    }
  }

  @RequiresEdt
  fun handleEnterPressed() {
    commandExecutor.startCommandExecution(editor.document.text)
  }

  @RequiresEdt
  fun performPaste(dataContext: DataContext? = null) {
    val context = dataContext ?: editor.dataContext
    editor.pasteProvider.performPaste(context)
  }

  @RequiresEdt
  fun showCommandHistory() {
    listeners.forEach { it.commandHistoryStateChanged(showing = true) }
  }

  @RequiresEdt
  fun onCommandHistoryClosed() {
    listeners.forEach { it.commandHistoryStateChanged(showing = false) }
  }

  @RequiresEdt
  fun showCommandSearch() {
    listeners.forEach { it.commandSearchRequested() }
  }

  fun addDocumentListener(listener: DocumentListener, disposable: Disposable? = null) {
    if (disposable != null) {
      editor.document.addDocumentListener(listener, disposable)
    }
    else editor.document.addDocumentListener(listener)
  }

  interface PromptStateListener {
    fun promptContentUpdated(renderingInfo: PromptRenderingInfo) {}
    fun commandHistoryStateChanged(showing: Boolean) {}
    fun commandSearchRequested() {}
    @RequiresEdt
    fun promptVisibilityChanged(visible: Boolean) {}
  }

  companion object {
    val KEY: DataKey<TerminalPromptController> = DataKey.create("TerminalPromptController")
  }
}