// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration.interpreter

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.intellij.python.junit5Tests.framework.env.pyMockInterpreterFixture
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * All Interpreters is a history of environments. The interpreter that a project uses is in use there and cannot be
 * removed, and one that no project uses any more can be.
 */
@Subsystems.Interpreters
@Layers.Functional
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
internal class PyAllInterpretersInUseTest {
  private val projectPath = tempPathFixture(prefix = "project")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val module = projectFixture.pyModuleFixture(projectPath, addPathToSourceRoot = true)
  private val pyProject = module.pyProjectFixture()
  private val firstEnvPath = tempPathFixture(prefix = "first")
  private val first = pyProject.pyMockInterpreterFixture(firstEnvPath)
  private val secondEnvPath = tempPathFixture(prefix = "second")
  private val second = pyProject.pyMockInterpreterFixture(secondEnvPath)

  @Test
  fun `the interpreter of a project is in use, and the one it left is not`(): Unit = runBlocking {
    withTimeout(10.seconds) {
      val model = EvoPyProjectModel.getInstance(projectFixture.get())

      val used = checkNotNull(pyProject.get().setPythonInterpreter(first.get()).orThrow())
      assertTrue(model.snapshot().isInUse(used), "the project uses it, so it cannot be removed")

      val picked = checkNotNull(pyProject.get().setPythonInterpreter(second.get()).orThrow())
      val snapshot = model.snapshot()
      assertTrue(snapshot.isInUse(picked))
      assertFalse(snapshot.isInUse(used), "no project uses it any more, so it can be removed")
    }
  }
}
