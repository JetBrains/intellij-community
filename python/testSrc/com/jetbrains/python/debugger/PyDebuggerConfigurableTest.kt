// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
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
}
