// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.venv.showCase

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pyEnvironmentFixture
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@PyEnvTestCase
class PyEnvWithVenvShowCaseTest {
  private val tempPathFixture = tempPathFixture()
  private val projectFixture = projectFixture()
  private val pyProjectFixture = projectFixture.pyProjectFixture(tempPathFixture)
  private val venvFixture = pyEnvironmentFixture().pyVenvFixture( // <-- venv fixture
    where = tempPathFixture,
    pyProjectFixture = pyProjectFixture
  )

  @Test
  fun venvTest(): Unit = timeoutRunBlocking {
    val venv = venvFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
    Assertions.assertTrue(venv in registry.interpreters(pyProjectFixture.get()), "The venv interpreter must be in the registry")
  }
}