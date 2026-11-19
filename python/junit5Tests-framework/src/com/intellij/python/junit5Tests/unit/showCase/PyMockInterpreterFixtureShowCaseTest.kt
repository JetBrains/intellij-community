// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.showCase

import com.intellij.python.junit5Tests.framework.env.pyMockInterpreterFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class PyMockInterpreterFixtureShowCaseTest {
  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture()
  // Every interpreter belongs to a Python project: here the main one, at the project root.
  private val pyProjectFixture = projectFixture.pyProjectFixture()
  private val interpreterFixture = pyProjectFixture.pyMockInterpreterFixture(pathFixture)

  @Test
  fun testMockSdk(): Unit = timeoutRunBlocking {
    val interpreter = interpreterFixture.get()
    val interpreters = PythonInterpreterRegistry.getInstance(projectFixture.get()).interpreters(pyProjectFixture.get())
    Assertions.assertTrue(interpreter in interpreters, "Interpreter creation failed")
  }
}