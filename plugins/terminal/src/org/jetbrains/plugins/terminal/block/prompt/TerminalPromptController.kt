// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.block.TerminalCommandExecutor
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellDataGeneratorsExecutorImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextProviderImpl
import org.jetbrains.plugins.terminal.block.history.CommandHistoryManager
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.ui.invokeLater
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.IS_PROMPT_EDITOR_KEY
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.Delegates

internal class TerminalPromptController(
  project: Project,
  private val editor: EditorEx,
  session: BlockTerminalSession,
  private val commandExecutor: TerminalCommandExecutor
) {
  private val commandHistoryManager: CommandHistoryManager
  private val listeners: MutableList<PromptStateListener> = CopyOnWriteArrayList()

  val model: TerminalPromptModel = TerminalPromptModelImpl(editor, session)

  val commandHistory: List<String>
    get() = commandHistoryManager.getHistory()

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

    val shellRuntimeContextProvider = ShellRuntimeContextProviderImpl(project, session)
    editor.putUserData(ShellRuntimeContextProviderImpl.KEY, shellRuntimeContextProvider)
    val shellGeneratorsExecutor = ShellDataGeneratorsExecutorImpl(session)
    editor.putUserData(ShellDataGeneratorsExecutorImpl.KEY, shellGeneratorsExecutor)

    commandHistoryManager = CommandHistoryManager(session, model)

    Disposer.register(session, model)

    val bufferReporting = ShellEditorBufferReportShellCommandListener(session) { buffer ->
      if (buffer.isNotBlank()) {
        invokeLater {
          model.commandText = buffer
          editor.caretModel.moveToOffset(editor.document.textLength)
        }
      }
    }

    session.addCommandListener(bufferReporting, session)
  }

  fun addListener(listener: PromptStateListener) {
    listeners.add(listener)
  }

  @RequiresEdt
  fun handleEnterPressed() {
    val customHandlers = TerminalPromptCustomEnterHandler.EP_NAME.extensionList
    for (handler in customHandlers) {
      val consumed = handler.handleEnter(model)
      if (consumed) return
    }
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

  companion object {
    val KEY: DataKey<TerminalPromptController> = DataKey.create("TerminalPromptController")
  }
}
