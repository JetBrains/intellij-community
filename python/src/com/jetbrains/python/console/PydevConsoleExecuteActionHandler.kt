// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.codeInsight.hint.HintManager
import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyLanguageFacade
import com.jetbrains.python.console.actions.CommandQueueForPythonConsoleService
import com.jetbrains.python.console.pydev.ConsoleCommunication
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStatementList
import java.awt.Font

open class PydevConsoleExecuteActionHandler(private val myConsoleView: LanguageConsoleView,
                                            processHandler: ProcessHandler,
                                            final override val consoleCommunication: ConsoleCommunication) : PythonConsoleExecuteActionHandler(processHandler, false), ConsoleCommunicationListener {

  private val project = myConsoleView.project
  private val myEnterHandler = PyConsoleEnterHandler()
  protected open var ipythonInputPromptCount = 2

  fun decreaseInputPromptCount(value : Int) {
    ipythonInputPromptCount -= value
  }

  override var isEnabled: Boolean = false
    set(value) {
      field = value
      updateConsoleState()
    }

  init {
    @Suppress("LeakingThis")
    this.consoleCommunication.addCommunicationListener(this)
  }

  override fun processLine(line: String) {
    executeMultiLine(line)
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
    val languageLevel = PyLanguageFacade.INSTANCE.getEffectiveLanguageLevel(project, myConsoleView.virtualFile)
    val pyFile = PyElementGenerator.getInstance(project).createDummyFile(languageLevel, text) as PyFile
    return PsiTreeUtil.findChildOfAnyType(pyFile, PyStatementList::class.java) == null && pyFile.statements.size < 2
  }

  private fun sendLineToConsole(code: ConsoleCommunication.ConsoleCodeFragment) {

    val consoleComm = consoleCommunication
    if (!consoleComm.isWaitingForInput) {
      executingPrompt()
    }
    if (ipythonEnabled && !consoleComm.isWaitingForInput && !code.text.isBlank()) {
      ++ipythonInputPromptCount
    }
    if (PyConsoleUtil.isCommandQueueEnabled(project)) {
      // add new command to CommandQueue service
      service<CommandQueueForPythonConsoleService>().addNewCommand(this, code)
    } else {
      consoleComm.execInterpreter(code) {}
    }
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
      if (PyConsoleUtil.isCommandQueueEnabled(project)) {
        inPrompt()
      } else {
        executingPrompt()
      }
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
      myConsoleView.indentPrompt = PyConsoleUtil.INDENT_PROMPT
      PyConsoleUtil.scrollDown(myConsoleView.currentEditor)
    }
  }

  private val ipythonEnabled: Boolean
    get() = PyConsoleUtil.getOrCreateIPythonData(myConsoleView.virtualFile).isIPythonEnabled

  protected open fun ipythonInPrompt() {
    ipythonInPrompt("In [$ipythonInputPromptCount]:")
  }

  protected fun ipythonInPrompt(prompt: String) {
    myConsoleView.promptAttributes = object : ConsoleViewContentType("", TextAttributes()) {
      override fun getAttributes(): TextAttributes {
        val attrs = EditorColorsManager.getInstance().globalScheme.getAttributes(USER_INPUT_KEY)
        attrs.fontType = Font.PLAIN
        return attrs
      }
    }

    val indentPrompt = PyConsoleUtil.IPYTHON_INDENT_PROMPT.padStart(prompt.length)
    myConsoleView.prompt = prompt
    myConsoleView.indentPrompt = indentPrompt
    PyConsoleUtil.scrollDown(myConsoleView.currentEditor)
  }

  private fun executingPrompt() {
    myConsoleView.prompt = PyConsoleUtil.EXECUTING_PROMPT
    myConsoleView.indentPrompt = PyConsoleUtil.EXECUTING_PROMPT
  }

  private fun waitingForInputPrompt() {
    if (PyConsoleUtil.INPUT_PROMPT != myConsoleView.prompt && PyConsoleUtil.HELP_PROMPT != myConsoleView.prompt) {
      myConsoleView.prompt = PyConsoleUtil.INPUT_PROMPT
      myConsoleView.indentPrompt = PyConsoleUtil.INPUT_PROMPT
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
      myConsoleView.indentPrompt = prompt
      PyConsoleUtil.scrollDown(myConsoleView.currentEditor)
    }
  }

  override fun commandExecuted(more: Boolean): Unit = updateConsoleState()

  override fun inputRequested() {
    isEnabled = true
  }

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
      if (PyConsoleUtil.isCommandQueueEnabled(project)) {
        doRunExecuteAction(console)
      } else {
        if (!canExecuteNow()) {
          HintManager.getInstance().showErrorHint(console.consoleEditor, prevCommandRunningMessage)
        }
        else {
          doRunExecuteAction(console)
        }
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
      deleteString(doc, endMarker)
      if (shouldCopyToHistory(console)) {
        (console as? PythonConsoleView)?.let { pythonConsole ->
          pythonConsole.flushDeferredText()
          pythonConsole.storeExecutionCounterLineNumber(ipythonInputPromptCount,
                                                        pythonConsole.historyViewer.document.lineCount +
                                                        console.consoleEditor.document.lineCount)
        }
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


    fun deleteString(document: Document, endMarker : RangeMarker) {
      if (endMarker.endOffset - endMarker.startOffset > 0) {
        ApplicationManager.getApplication().runWriteAction {
          CommandProcessor.getInstance().runUndoTransparentAction {
            document.deleteString(endMarker.startOffset, endMarker.endOffset)
          }
        }
      }
    }
  }
}

private var LanguageConsoleView.indentPrompt: String
  get() {
    return (this as? LanguageConsoleImpl)?.consolePromptDecorator?.indentPrompt ?: ""
  }
  set(value) {
    (this as? LanguageConsoleImpl)?.consolePromptDecorator?.indentPrompt = value
  }
