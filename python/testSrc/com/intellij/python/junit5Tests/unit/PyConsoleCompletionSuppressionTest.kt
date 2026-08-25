// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.Function
import com.jetbrains.python.console.PyConsoleUtil
import com.jetbrains.python.console.PydevConsoleRunner
import com.jetbrains.python.console.pydev.ConsoleCommunication
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener
import com.jetbrains.python.console.pydev.InterpreterResponse
import com.jetbrains.python.console.pydev.PydevCompletionVariant
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * While a console waits for the input of the running program (the `>?` prompt), the text typed into the input
 * editor is passed to the stdin of the process as is. Completing it would offer Python names and let a lookup
 * item replace what the user typed, so the program would read text that was never entered (PY-75723).
 */
@TestApplication
class PyConsoleCompletionSuppressionTest {
  private val projectFixture = projectFixture(openAfterCreation = true)

  private val project get() = projectFixture.get()

  private fun consoleFile(communication: ConsoleCommunication?): PsiFile = runReadAction {
    PyExpressionCodeFragmentImpl(project, "console.py", "", true).also { file ->
      if (communication != null) {
        file.putCopyableUserData(PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY, communication)
      }
    }
  }

  @Test
  fun `a file that belongs to no console completes`() {
    assertFalse(PyConsoleUtil.isCodeCompletionSuppressed(consoleFile(null)))
  }

  @Test
  fun `a console at the ordinary prompt completes`() {
    val file = consoleFile(FakeConsoleCommunication(waitingForInput = false))
    assertFalse(PyConsoleUtil.isCodeCompletionSuppressed(file))
  }

  @Test
  fun `a console waiting for the input of the program does not complete`() {
    val file = consoleFile(FakeConsoleCommunication(waitingForInput = true))
    assertTrue(PyConsoleUtil.isCodeCompletionSuppressed(file))
  }

  private class FakeConsoleCommunication(private val waitingForInput: Boolean) : ConsoleCommunication {
    override fun getCompletions(text: String?, actualToken: String?): List<PydevCompletionVariant> = emptyList()
    override fun getDescription(text: String?): String = ""
    override fun isWaitingForInput(): Boolean = waitingForInput
    override fun isExecuting(): Boolean = false
    override fun needsMore(): Boolean = false
    override fun execInterpreter(code: ConsoleCommunication.ConsoleCodeFragment, callback: Function<InterpreterResponse, Any>) = Unit
    override fun interrupt() = Unit
    override fun addCommunicationListener(listener: ConsoleCommunicationListener) = Unit
    override fun notifyCommandExecuted(more: Boolean) = Unit
    override fun notifyInputRequested() = Unit
    override fun notifyInputReceived() = Unit
  }
}
