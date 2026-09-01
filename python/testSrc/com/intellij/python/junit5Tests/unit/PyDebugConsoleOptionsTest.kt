// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.console.PyConsoleOptions
import com.jetbrains.python.console.PythonDebugLanguageConsoleView
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A project configured before the split has no Debug Console value, so the accessor reads the Python Console
 * one. Setting a Debug Console value must not change the Python Console.
 */
@TestApplication
internal class PyDebugConsoleOptionsTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `unset Debug Console values fall back to the Python Console ones`() {
    val project = projectFixture.get()
    PyConsoleOptions.getInstance(project).loadState(PyConsoleOptions.State().also {
      it.myIpythonEnabled = false
      it.myCommandQueueEnabled = true
    })
    PyDebuggerOptionsProvider.getInstance(project).loadState(PyDebuggerOptionsProvider.State())

    assertEquals(false, PyDebuggerOptionsProvider.getInstance(project).isDebugConsoleIpythonEnabled)
    assertEquals(true, PyDebuggerOptionsProvider.getInstance(project).isDebugConsoleCommandQueueEnabled)
  }

  @Test
  fun `a Debug Console value does not change the Python Console one`() {
    val project = projectFixture.get()
    PyConsoleOptions.getInstance(project).loadState(PyConsoleOptions.State().also { it.myIpythonEnabled = false })
    PyDebuggerOptionsProvider.getInstance(project).loadState(PyDebuggerOptionsProvider.State())

    PyDebuggerOptionsProvider.getInstance(project).isDebugConsoleIpythonEnabled = true

    assertEquals(true, PyDebuggerOptionsProvider.getInstance(project).isDebugConsoleIpythonEnabled)
    assertEquals(false, PyConsoleOptions.getInstance(project).isIpythonEnabled)
  }

  @Test
  fun `the start script defaults to the command the Debug Console used to hardcode`() {
    val project = projectFixture.get()
    PyDebuggerOptionsProvider.getInstance(project).loadState(PyDebuggerOptionsProvider.State())

    assertEquals(
      PythonDebugLanguageConsoleView.DEBUG_CONSOLE_START_COMMAND,
      PyDebuggerOptionsProvider.getInstance(project).debugConsoleStartScript,
    )
  }
}
