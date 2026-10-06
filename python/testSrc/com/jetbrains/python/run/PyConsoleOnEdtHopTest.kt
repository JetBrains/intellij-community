// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.ExecutionException
import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Pins the one EDT hop a Python launch still needs: `PythonCommandLineState.computeOnEdt`.
 *
 * Everything else about a launch now runs off the EDT (PY-89567), and a console can only be built on it, so this
 * hop marshals both the console and any failure across the thread boundary. The failure half matters as much as
 * the value: `ExecutionManager` renders an [ExecutionException] as a launch error, and anything else as an
 * internal one, so the hop must not wrap or replace what the console factory threw.
 */
@TestApplication
@Subsystems.Run
@Layers.Functional
@TestFor(issues = ["PY-89567"])
class PyConsoleOnEdtHopTest {
  @Test
  fun `the computation runs on the EDT and its value reaches the caller`(): Unit = timeoutRunBlocking {
    var ranOnEdt: Boolean? = null

    val value = PythonCommandLineState.computeOnEdt {
      ranOnEdt = ApplicationManager.getApplication().isDispatchThread
      "console"
    }

    assertThat(ranOnEdt).describedAs("the computation must run on the EDT").isEqualTo(true)
    assertThat(value).isEqualTo("console")
  }

  @Test
  fun `an ExecutionException reaches the caller as itself`(): Unit = timeoutRunBlocking {
    val failure = ExecutionException("the console could not be built")

    assertThatThrownBy { PythonCommandLineState.computeOnEdt<Any> { throw failure } }
      .describedAs("the launch failure must not be wrapped or replaced on the way back from the EDT")
      .isSameAs(failure)
  }

  @Test
  fun `a runtime failure reaches the caller as itself`(): Unit = timeoutRunBlocking {
    val failure = IllegalStateException("the console factory is broken")

    assertThatThrownBy { PythonCommandLineState.computeOnEdt<Any> { throw failure } }.isSameAs(failure)
  }
}
