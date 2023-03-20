// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger

import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.xdebugger.XDebugSessionListener
import com.jetbrains.python.PyBundle
import com.jetbrains.python.console.PydevConsoleExecuteActionHandler
import com.jetbrains.python.console.PythonDebugLanguageConsoleView
import com.jetbrains.python.console.pydev.ConsoleCommunication
import org.jetbrains.annotations.Nls

open class PydevDebugConsoleExecuteActionHandler(private val myConsole: PythonDebugLanguageConsoleView,
                                                 myProcessHandler: ProcessHandler,
                                                 consoleCommunication: ConsoleCommunication) : PydevConsoleExecuteActionHandler(myConsole.pydevConsoleView, myProcessHandler, consoleCommunication), XDebugSessionListener {

  override val consoleIsNotEnabledMessage: @Nls String
    get() = PyBundle.message("debugger.pydev.console.pause.the.process.to.use.command.line")

  override fun sessionPaused() {
    isEnabled = true
  }

  override fun sessionResumed() {
    isEnabled = false
  }

  override fun sessionStopped() {
    isEnabled = false
  }

  override fun stackFrameChanged() {
  }

  override fun beforeSessionResume() {
  }

  override fun beforeExecution(consoleView: LanguageConsoleView) {
    super.beforeExecution(consoleView)
    val text = (consoleView as LanguageConsoleImpl).currentEditor.document.text
    myConsole.primaryConsoleView.print(text + '\n', ConsoleViewContentType.NORMAL_OUTPUT)
  }
}
