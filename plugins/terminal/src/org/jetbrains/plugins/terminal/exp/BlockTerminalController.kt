// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindUtil
import com.intellij.find.SearchSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.exp.BlockTerminalSearchSession.Companion.isSearchInBlock
import java.util.concurrent.CopyOnWriteArrayList

class BlockTerminalController(
  private val project: Project,
  private val session: BlockTerminalSession,
  private val outputController: TerminalOutputController,
  private val promptController: TerminalPromptController,
  private val selectionController: TerminalSelectionController,
  private val focusModel: TerminalFocusModel
) : ShellCommandListener {
  private val listeners: MutableList<BlockTerminalControllerListener> = CopyOnWriteArrayList()
  private val promptVisibilityAlarm: Alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)

  var searchSession: BlockTerminalSearchSession? = null
    private set

  init {
    session.addCommandListener(this)

    // Show initial terminal output (prior to the first prompt) in a separate block.
    // `initialized` event will finish the block.
    outputController.startCommandBlock(command = null, promptText = null)
    promptController.promptIsVisible = false
    session.model.isCommandRunning = true
  }

  fun resize(newSize: TermSize) {
    session.postResize(newSize)
  }

  @RequiresEdt
  fun startCommandExecution(command: String) {
    promptController.reset()
    if (command.isBlank()) {
      outputController.insertEmptyLine()
    }
    else startCommand(command)
  }

  private fun startCommand(command: String) {
    outputController.startCommandBlock(command, promptText = promptController.promptText)
    // Hide the prompt with delay, so it will be possible to cancel hide request if command is extra fast.
    // And there will be no blinking of prompt in this case.
    promptVisibilityAlarm.addRequest(Runnable {
      promptController.promptIsVisible = false
    }, 50)
    session.commandManager.sendCommandToExecute(command)
    session.model.isCommandRunning = true
  }

  override fun initialized(currentDirectory: String?) {
    finishCommandBlock(exitCode = 0)
  }

  override fun commandFinished(command: String?, exitCode: Int, duration: Long?) {
    finishCommandBlock(exitCode)
  }

  private fun finishCommandBlock(exitCode: Int) {
    outputController.finishCommandBlock(exitCode)

    val model = session.model
    model.isCommandRunning = false

    promptVisibilityAlarm.cancelAllRequests()
    invokeLater {
      promptController.promptIsVisible = true
    }
  }

  @RequiresEdt
  fun startSearchSession() {
    val findModel = FindModel()
    findModel.copyFrom(FindManager.getInstance(project).findInFileModel)
    findModel.isWholeWordsOnly = false
    findModel.isSearchInBlock = selectionController.primarySelection != null
    val editor = outputController.outputModel.editor
    FindUtil.configureFindModel(false, editor, findModel, false)
    findModel.isGlobal = false
    val session = BlockTerminalSearchSession(project, editor, findModel, outputController.outputModel, outputController.selectionModel,
                                             closeCallback = this::onSearchClosed)
    searchSession = session
    listeners.forEach { it.searchSessionStarted(session) }
    session.component.requestFocusInTheSearchFieldAndSelectContent(project)
  }

  @RequiresEdt
  fun activateSearchSession() {
    val session = searchSession ?: return
    val editor = outputController.outputModel.editor
    session.component.requestFocusInTheSearchFieldAndSelectContent(project)
    FindUtil.configureFindModel(false, editor, session.findModel, false)
    session.findModel.isSearchInBlock = selectionController.primarySelection != null
    session.findModel.isGlobal = false
  }

  @RequiresEdt
  fun finishSearchSession() {
    searchSession?.close()
  }

  private fun onSearchClosed() {
    searchSession?.let { session -> listeners.forEach { it.searchSessionFinished(session) } }
    searchSession = null
    if (selectionController.primarySelection != null || session.model.isCommandRunning) {
      focusModel.focusOutput()
    }
    else focusModel.focusPrompt()
  }

  fun addListener(listener: BlockTerminalControllerListener, disposable: Disposable? = null) {
    listeners.add(listener)
    if (disposable != null) {
      Disposer.register(disposable) { listeners.remove(listener) }
    }
  }

  interface BlockTerminalControllerListener {
    fun searchSessionStarted(session: SearchSession) {}
    fun searchSessionFinished(session: SearchSession) {}
  }

  companion object {
    val KEY: DataKey<BlockTerminalController> = DataKey.create("BlockTerminalController")
  }
}