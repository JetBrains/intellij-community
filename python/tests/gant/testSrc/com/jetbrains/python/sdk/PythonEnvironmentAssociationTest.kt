// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.python.community.impl.conda.environment.CondaEnvironment
import com.intellij.python.sdk.backend.SystemPythonEnvironment
import com.intellij.python.venv.environment.VenvEnvironment
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Which environments must record the project they belong to.
 *
 * This is the decision PY-91967 turns on. An interpreter with no working directory and no associated path used to be
 * reported as invalid whatever its kind, which marked every shared installation broken. Each kind lives in its own
 * module, so the test sits where all of them are loaded.
 *
 * @see com.intellij.python.sdk.backend.PythonEnvironment.requiresAssociation
 */
@Subsystems.Interpreters
@Layers.Functional
@TestApplication
internal class PythonEnvironmentAssociationTest {
  private val binary: Path = Path.of("/envs/myenv/bin/python")
  private val root: Path = Path.of("/envs/myenv")

  @Test
  @DisplayName("a system installation belongs to no project")
  fun `system python requires no association`() {
    assertFalse(SystemPythonEnvironment(pythonBinaryPath = binary).requiresAssociation)
  }

  @Test
  @DisplayName("a virtual environment is created for one project")
  fun `venv requires association`() {
    val venv = VenvEnvironment(
      version = "3.12.1",
      pythonBinaryPath = binary,
      pythonHomePath = root,
      config = emptyMap(),
      libRoot = root.resolve("lib"),
    )
    assertTrue(venv.requiresAssociation)
  }

  @Test
  @DisplayName("a named conda environment is created for one project, the base installation is not")
  fun `only a non base conda environment requires association`() {
    assertTrue(condaEnvironment(isBase = false).requiresAssociation)
    assertFalse(condaEnvironment(isBase = true).requiresAssociation)
  }

  private fun condaEnvironment(isBase: Boolean) = CondaEnvironment(
    version = "3.12.1",
    pythonBinaryPath = binary,
    pythonHomePath = root,
    condaMetaPath = root.resolve("conda-meta"),
    isBase = isBase,
  )
}
