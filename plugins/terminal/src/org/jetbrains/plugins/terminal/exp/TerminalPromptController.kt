// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.Delegates

class TerminalPromptController(
  private val editor: EditorEx,
  session: TerminalSession,
  private val commandExecutor: TerminalCommandExecutor
) : ShellCommandListener {
  private val completionManager: TerminalCompletionManager?
  private val commandHistoryManager: CommandHistoryManager
  private val listeners: MutableList<PromptStateListener> = CopyOnWriteArrayList()

  val commandHistory: List<String>
    get() = commandHistoryManager.history

  var promptIsVisible: Boolean by Delegates.observable(true) { _, oldValue, newValue ->
    if (newValue != oldValue) listeners.forEach { it.promptVisibilityChanged(newValue) }
  }

  init {
    completionManager = when (session.shellIntegration?.shellType) {
      ShellType.ZSH -> ZshCompletionManager(session)
      ShellType.BASH -> BashCompletionManager(session)
      else -> null
    }
    editor.putUserData(KEY, this)  // to access this object from editor action handlers
    editor.putUserData(TerminalSession.KEY, session)
    editor.putUserData(TerminalCompletionManager.KEY, completionManager)

    commandHistoryManager = CommandHistoryManager(session)
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

  override fun directoryChanged(newDirectory: String) {
    val newText = computePromptText(newDirectory)
    listeners.forEach { it.promptLabelChanged(newText) }
  }

  fun computePromptText(directory: String): @NlsSafe String {
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
    val KEY: Key<TerminalPromptController> = Key.create("TerminalPromptController")
  }
}