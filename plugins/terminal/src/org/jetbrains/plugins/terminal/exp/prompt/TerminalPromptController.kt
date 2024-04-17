// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalColorPalette
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.ShellCommandListener
import org.jetbrains.plugins.terminal.exp.TerminalCommandExecutor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.IS_PROMPT_EDITOR_KEY
import org.jetbrains.plugins.terminal.exp.completion.IJShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.completion.ShellCommandExecutor
import org.jetbrains.plugins.terminal.exp.completion.ShellCommandExecutorImpl
import org.jetbrains.plugins.terminal.exp.history.CommandHistoryManager
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.Delegates

class TerminalPromptController(
  private val editor: EditorEx,
  session: BlockTerminalSession,
  private val commandExecutor: TerminalCommandExecutor
) {
  private val commandHistoryManager: CommandHistoryManager
  private val listeners: MutableList<PromptStateListener> = CopyOnWriteArrayList()

  val model: TerminalPromptModel = TerminalPromptModel(editor, TerminalSessionInfoImpl(session))

  val commandHistory: List<String>
    get() = commandHistoryManager.history

  // should be accessed in EDT
  var promptIsVisible: Boolean by Delegates.observable(true) { _, oldValue, newValue ->
    if (newValue != oldValue) listeners.forEach { it.promptVisibilityChanged(newValue) }
  }

  init {
    editor.putUserData(IS_PROMPT_EDITOR_KEY, true)
    editor.putUserData(TerminalPromptModel.KEY, model)
    editor.putUserData(BlockTerminalSession.KEY, session)
    // Used in TerminalPromptFileViewProvider
    editor.virtualFile.putUserData(TerminalPromptModel.KEY, model)
    editor.virtualFile.putUserData(ShellType.KEY, session.shellIntegration.shellType)

    val shellCommandExecutor = ShellCommandExecutorImpl(session)
    editor.putUserData(ShellCommandExecutor.KEY, shellCommandExecutor)
    val runtimeDataProvider = IJShellRuntimeDataProvider(session, shellCommandExecutor)
    editor.putUserData(IJShellRuntimeDataProvider.KEY, runtimeDataProvider)

    commandHistoryManager = CommandHistoryManager(session)

    Disposer.register(session, model)
    session.addCommandListener(object : ShellCommandListener {
      override fun promptStateUpdated(newState: TerminalPromptState) {
        model.updatePrompt(newState)
      }
    })
  }

  fun addListener(listener: PromptStateListener) {
    listeners.add(listener)
  }

  @RequiresEdt
  fun handleEnterPressed() {
    commandExecutor.startCommandExecution(model.commandText)
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

  interface PromptStateListener {
    fun commandHistoryStateChanged(showing: Boolean) {}
    fun commandSearchRequested() {}
    @RequiresEdt
    fun promptVisibilityChanged(visible: Boolean) {}
  }

  private class TerminalSessionInfoImpl(private val session: BlockTerminalSession) : TerminalSessionInfo {
    override val settings: JBTerminalSystemSettingsProviderBase = session.settings
    override val colorPalette: TerminalColorPalette = session.colorPalette
    override val terminalSize: TermSize
      get() = session.model.withContentLock { TermSize(session.model.width, session.model.height) }
  }

  companion object {
    val KEY: DataKey<TerminalPromptController> = DataKey.create("TerminalPromptController")
  }
}