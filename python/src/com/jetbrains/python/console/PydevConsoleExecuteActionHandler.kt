/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.console

import com.intellij.codeInsight.hint.HintManager
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.console.pydev.ConsoleCommunication
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStatementList
import java.awt.Font

/**
 * @author traff
 */
open class PydevConsoleExecuteActionHandler(private val myConsoleView: LanguageConsoleView,
                                            processHandler: ProcessHandler,
                                            final override val consoleCommunication: ConsoleCommunication) : PythonConsoleExecuteActionHandler(processHandler, false), ConsoleCommunicationListener {

  private val project = myConsoleView.project
  private val myEnterHandler = PyConsoleEnterHandler()
  private var myIpythonInputPromptCount = 2

  override var isEnabled = false
    set(value) {
      field = value
      updateConsoleState()
    }

  init {
    this.consoleCommunication.addCommunicationListener(this)
  }

  override fun processLine(text: String) {
    executeMultiLine(text)
  }

  private fun executeMultiLine(text: String) {
    val commandText = if (!text.endsWith("\n")) {
      text + "\n"
    }
    else {
      text
    }
    sendLineToConsole(ConsoleCommunication.ConsoleCodeFragment(commandText, checkSingleLine(text)))
  }

  override fun checkSingleLine(text: String): Boolean {
    val pyFile: PyFile =PyElementGenerator.getInstance(project).createDummyFile(myConsoleView.virtualFile.getUserData(LanguageLevel.KEY), text) as PyFile
    return PsiTreeUtil.findChildOfAnyType(pyFile, PyStatementList::class.java) == null && pyFile.statements.size < 2
  }

  private fun sendLineToConsole(code: ConsoleCommunication.ConsoleCodeFragment) {

    val consoleComm = consoleCommunication
    if (!consoleComm.isWaitingForInput) {
      executingPrompt()
    }
    if (ipythonEnabled && !consoleComm.isWaitingForInput && !code.getText().isBlank()) {
      ++myIpythonInputPromptCount;
    }

    consoleComm.execInterpreter(code) {}
  }

  override fun updateConsoleState() {
    if (!isEnabled) {
      executingPrompt()
    }
    else if (consoleCommunication.isWaitingForInput) {
      waitingForInputPrompt()
    }
    else if (canExecuteNow()) {
      if (consoleCommunication.needsMore()) {
        more()
      }
      else {
        inPrompt()
      }
    }
    else {
      executingPrompt()
    }
  }

  private fun inPrompt() {
    if (ipythonEnabled) {
      ipythonInPrompt()
    }
    else {
      ordinaryPrompt()
    }
  }

  private fun ordinaryPrompt() {
    if (PyConsoleUtil.ORDINARY_PROMPT != myConsoleView.prompt) {
      myConsoleView.prompt = PyConsoleUtil.ORDINARY_PROMPT
      PyConsoleUtil.scrollDown(myConsoleView.currentEditor)
    }
  }

  private val ipythonEnabled: Boolean
    get() = PyConsoleUtil.getOrCreateIPythonData(myConsoleView.virtualFile).isIPythonEnabled

  private fun ipythonInPrompt() {
    myConsoleView.setPromptAttributes(object : ConsoleViewContentType("", ConsoleViewContentType.USER_INPUT_KEY) {
      override fun getAttributes(): TextAttributes {
        val attrs = super.getAttributes()
        attrs.fontType = Font.PLAIN
        return attrs
      }
    })

    myConsoleView.prompt = "In[$myIpythonInputPromptCount]:"
    PyConsoleUtil.scrollDown(myConsoleView.currentEditor)
  }

  private fun executingPrompt() {
    myConsoleView.prompt = PyConsoleUtil.EXECUTING_PROMPT
  }

  private fun waitingForInputPrompt() {
    if (PyConsoleUtil.INPUT_PROMPT != myConsoleView.prompt && PyConsoleUtil.HELP_PROMPT != myConsoleView.prompt) {
      myConsoleView.prompt = PyConsoleUtil.INPUT_PROMPT
      PyConsoleUtil.scrollDown(myConsoleView.currentEditor)
    }
  }

  private fun more() {
    val prompt = if (ipythonEnabled) {
      PyConsoleUtil.IPYTHON_INDENT_PROMPT
    }
    else {
      PyConsoleUtil.INDENT_PROMPT
    }
    if (prompt != myConsoleView.prompt) {
      myConsoleView.prompt = prompt
      PyConsoleUtil.scrollDown(myConsoleView.currentEditor)
    }
  }

  override fun commandExecuted(more: Boolean) = updateConsoleState()

  override fun inputRequested() {
    isEnabled = true
  }

  val pythonIndent: Int
    get() = CodeStyleSettingsManager.getSettings(project).getIndentSize(PythonFileType.INSTANCE)

  override val cantExecuteMessage: String
    get() {
      if (!isEnabled) {
        return consoleIsNotEnabledMessage
      }
      else if (!canExecuteNow()) {
        return prevCommandRunningMessage
      }
      else {
        return "Can't execute the command"
      }
    }

  override fun runExecuteAction(console: LanguageConsoleView) {
    if (isEnabled) {
      if (!canExecuteNow()) {
        HintManager.getInstance().showErrorHint(console.consoleEditor, prevCommandRunningMessage)
      }
      else {
        doRunExecuteAction(console)
      }
    }
    else {
      HintManager.getInstance().showErrorHint(console.consoleEditor, consoleIsNotEnabledMessage)
    }
  }

  private fun doRunExecuteAction(console: LanguageConsoleView) {
    val doc = myConsoleView.editorDocument
    val endMarker = doc.createRangeMarker(doc.textLength, doc.textLength)
    endMarker.isGreedyToLeft = false
    endMarker.isGreedyToRight = true
    val isComplete = myEnterHandler.handleEnterPressed(console.consoleEditor)
    if (isComplete || consoleCommunication.isWaitingForInput) {

      if (endMarker.endOffset - endMarker.startOffset > 0) {
        ApplicationManager.getApplication().runWriteAction {
          CommandProcessor.getInstance().runUndoTransparentAction {
            doc.deleteString(endMarker.startOffset, endMarker.endOffset)
          }
        }
      }
      if (shouldCopyToHistory(console)) {
        copyToHistoryAndExecute(console)
      }
      else {
        processLine(myConsoleView.consoleEditor.document.text)
      }
    }
  }

  private fun copyToHistoryAndExecute(console: LanguageConsoleView) = super.runExecuteAction(console)

  override fun canExecuteNow(): Boolean = !consoleCommunication.isExecuting || consoleCommunication.isWaitingForInput

  protected open val consoleIsNotEnabledMessage: String
    get() = notEnabledMessage

  companion object {

    val prevCommandRunningMessage: String
      get() = "Previous command is still running. Please wait or press Ctrl+C in console to interrupt."

    val notEnabledMessage: String
      get() = "Console is not enabled."

    private fun shouldCopyToHistory(console: LanguageConsoleView): Boolean {
      return !PyConsoleUtil.isPagingPrompt(console.prompt)
    }
  }
}
