// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.ui.components.JBRadioButton
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@Subsystems.Debugger
@Layers.Functional
internal class PyDebuggerConfigurableTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `apply without backend change keeps default backend marker`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val settings = PyDebuggerOptionsProvider.getInstance(projectFixture.get())
    settings.loadState(PyDebuggerOptionsProvider.State().also {
      it.myDebuggerBackend = PyDebuggerOptionsProvider.DEFAULT_BACKEND_MARKER
    })

    val configurable = PyDebuggerConfigurable(projectFixture.get())
    configurable.createComponent()
    configurable.reset()

    configurable.apply()

    assertThat(settings.state.myDebuggerBackend).isEqualTo(PyDebuggerOptionsProvider.DEFAULT_BACKEND_MARKER)
  }

  @Test
  fun `the mode radio group switches the backend on apply`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val project = projectFixture.get()
    val settings = PyDebuggerOptionsProvider.getInstance(project)
    settings.loadState(PyDebuggerOptionsProvider.State().also {
      it.myDebuggerBackend = PyDebuggerBackend.PYDEVD.name
    })

    val configurable = PyDebuggerConfigurable(project)
    val component = configurable.createComponent()
    configurable.reset()
    assertThat(configurable.isModified).isFalse()

    val debugpyButton = UIUtil.uiTraverser(component)
      .filter(JBRadioButton::class.java)
      .first { it.text == "debugpy" }
    // The button is disabled while the Python DAP plugin is absent, and a disabled button ignores doClick().
    // Drive the model instead: this test covers the binding between the radio group and the stored backend.
    debugpyButton.isSelected = true

    assertThat(configurable.isModified).isTrue()
    configurable.apply()

    assertThat(settings.selectedBackend).isEqualTo(PyDebuggerBackend.DEBUGPY)
  }
}
