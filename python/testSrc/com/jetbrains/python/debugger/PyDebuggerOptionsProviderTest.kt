// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class PyDebuggerOptionsProviderTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private fun settings() = PyDebuggerOptionsProvider.getInstance(projectFixture.get())

  private fun stateWith(marker: String) = PyDebuggerOptionsProvider.State().also { it.myDebuggerBackend = marker }

  @Test
  @RunMethodInEdt
  fun `fresh State holds DEFAULT marker`() {
    assertThat(PyDebuggerOptionsProvider.State().myDebuggerBackend)
      .isEqualTo(PyDebuggerOptionsProvider.DEFAULT_BACKEND_MARKER)
  }

  @Test
  @RunMethodInEdt
  fun `getSelectedBackend resolves DEFAULT marker to global default`() {
    settings().loadState(stateWith(PyDebuggerOptionsProvider.DEFAULT_BACKEND_MARKER))
    assertThat(settings().selectedBackend).isEqualTo(DEFAULT_PY_DEBUGGER_BACKEND)
  }

  @Test
  @RunMethodInEdt
  fun `getSelectedBackend returns PYDEVD when stored as PYDEVD`() {
    settings().loadState(stateWith(PyDebuggerBackend.PYDEVD.name))
    assertThat(settings().selectedBackend).isEqualTo(PyDebuggerBackend.PYDEVD)
  }

  @Test
  @RunMethodInEdt
  fun `getSelectedBackend returns DEBUGPY when stored as DEBUGPY`() {
    settings().loadState(stateWith(PyDebuggerBackend.DEBUGPY.name))
    assertThat(settings().selectedBackend).isEqualTo(PyDebuggerBackend.DEBUGPY)
  }

  @Test
  @RunMethodInEdt
  fun `getSelectedBackend resolves unknown stored values to global default`() {
    settings().loadState(stateWith("SOME_FUTURE_NAME"))
    assertThat(settings().selectedBackend).isEqualTo(DEFAULT_PY_DEBUGGER_BACKEND)
  }

  @Test
  @RunMethodInEdt
  fun `setSelectedBackend(PYDEVD) persists in State`() {
    settings().loadState(stateWith(PyDebuggerOptionsProvider.DEFAULT_BACKEND_MARKER))
    settings().selectedBackend = PyDebuggerBackend.PYDEVD
    assertThat(settings().state.myDebuggerBackend).isEqualTo(PyDebuggerBackend.PYDEVD.name)
  }

  @Test
  @RunMethodInEdt
  fun `setSelectedBackend(DEBUGPY) persists in State`() {
    settings().loadState(stateWith(PyDebuggerOptionsProvider.DEFAULT_BACKEND_MARKER))
    settings().selectedBackend = PyDebuggerBackend.DEBUGPY
    assertThat(settings().state.myDebuggerBackend).isEqualTo(PyDebuggerBackend.DEBUGPY.name)
  }

  @Test
  @RunMethodInEdt
  fun `round-trip across loadState preserves explicit choice`() {
    settings().loadState(stateWith(PyDebuggerOptionsProvider.DEFAULT_BACKEND_MARKER))
    settings().selectedBackend = PyDebuggerBackend.PYDEVD
    val captured = settings().state

    settings().loadState(captured)

    assertThat(settings().selectedBackend).isEqualTo(PyDebuggerBackend.PYDEVD)
    assertThat(settings().state.myDebuggerBackend).isEqualTo(PyDebuggerBackend.PYDEVD.name)
  }
}
