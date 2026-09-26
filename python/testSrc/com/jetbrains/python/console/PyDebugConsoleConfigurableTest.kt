// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Does the Starting script editor on the Debug Console page actually store what the user typed? PY-91913. */
@Subsystems.Debugger
@Layers.Functional
@TestApplication
internal class PyDebugConsoleConfigurableTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `the starting script editor stores its text on apply`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val project = projectFixture.get()
    val options = PyDebuggerOptionsProvider.getInstance(project)
    options.loadState(PyDebuggerOptionsProvider.State())

    val configurable = PyDebugConsoleConfigurable(project)
    configurable.createComponent()
    configurable.reset()
    assertThat(configurable.isModified).isFalse()

    configurable.setStartScriptTextForTest("import pprint")

    assertThat(configurable.isModified).isTrue()
    configurable.apply()

    assertThat(options.debugConsoleStartScript).isEqualTo("import pprint")
  }
}
