// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.IS_PROMPT_EDITOR_KEY
import org.jetbrains.plugins.terminal.exp.completion.IJShellRuntimeDataProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.Delegates

class TerminalPromptController(
  project: Project,
  private val editor: EditorEx,
  session: TerminalSession,
  private val commandExecutor: TerminalCommandExecutor
) : ShellCommandListener {
  private val commandHistoryManager: CommandHistoryManager
  private val listeners: MutableList<PromptStateListener> = CopyOnWriteArrayList()

  val commandHistory: List<String>
    get() = commandHistoryManager.history

  var promptIsVisible: Boolean by Delegates.observable(true) { _, oldValue, newValue ->
    if (newValue != oldValue) listeners.forEach { it.promptVisibilityChanged(newValue) }
  }

  var promptText: String = computePromptText(TerminalProjectOptionsProvider.getInstance(project).startingDirectory ?: "")
    private set(value) {
      if (value != field) {
        field = value
        listeners.forEach { it.promptLabelChanged(value) }
      }
    }

  val commandText: String
    get() = editor.document.text

  init {
    editor.putUserData(IS_PROMPT_EDITOR_KEY, true)
    editor.putUserData(TerminalSession.KEY, session)

    val runtimeDataProvider = IJShellRuntimeDataProvider(session)
    editor.putUserData(IJShellRuntimeDataProvider.KEY, runtimeDataProvider)

    commandHistoryManager = CommandHistoryManager(session)
    session.addCommandListener(this)
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

  override fun initialized(currentDirectory: String?) {
    if (currentDirectory != null) {
      promptText = computePromptText(currentDirectory)
    }
  }

  override fun directoryChanged(newDirectory: String) {
    promptText = computePromptText(newDirectory)
  }

  private fun computePromptText(directory: String): @NlsSafe String {
    return if (directory != SystemProperties.getUserHome()) {
      FileUtil.getLocationRelativeToUserHome(directory)
    }
    else "~"
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

  fun addDocumentListener(listener: DocumentListener, disposable: Disposable? = null) {
    if (disposable != null) {
      editor.document.addDocumentListener(listener, disposable)
    }
    else editor.document.addDocumentListener(listener)
  }

  interface PromptStateListener {
    fun promptLabelChanged(newText: @NlsSafe String) {}
    fun commandHistoryStateChanged(showing: Boolean) {}
    fun promptVisibilityChanged(visible: Boolean) {}
  }

  companion object {
    val KEY: DataKey<TerminalPromptController> = DataKey.create("TerminalPromptController")
  }
}