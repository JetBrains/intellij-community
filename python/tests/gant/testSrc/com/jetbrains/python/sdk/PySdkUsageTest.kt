// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.PythonInterpreterUpdateRequest
import com.intellij.python.sdk.backend.PythonInterpreterUpdater
import com.intellij.python.sdk.backend.addMockPythonInterpreter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * A background process asks for an update of an interpreter whenever something changes under its paths. Such an update
 * generates skeletons and scans packages, which starts the interpreter, so it must run only for an interpreter that the
 * project uses. See PY-88315.
 */
@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PySdkUsageTest {
  private val projectFixture = projectFixture()
  private val pyProjectFixture = projectFixture.pyProjectFixture()

  @Test
  fun `an interpreter that no Python project uses is not in use`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(project)
    val interpreter = registry.addMockPythonInterpreter(pyProject, PythonMockSdk.create())
    try {
      assertThat(EvoPyProjectModel.getInstance(project).snapshot().interpreters).doesNotContain(interpreter)
    }
    finally {
      registry.remove(interpreter)
    }
  }

  @Test
  fun `the interpreter of a Python project is in use`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(project)
    val interpreter = registry.addMockPythonInterpreter(pyProject, PythonMockSdk.create())
    try {
      pyProject.setPythonInterpreter(interpreter)
      assertThat(EvoPyProjectModel.getInstance(project).snapshot().interpreters).contains(interpreter)
    }
    finally {
      pyProject.setPythonInterpreter(null)
      registry.remove(interpreter)
    }
  }

  @Test
  fun `a background update of an interpreter that no Python project uses is rejected`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(project)
    val interpreter = registry.addMockPythonInterpreter(pyProject, PythonMockSdk.create())
    try {
      // The check reads the current snapshot, so it must hold the interpreter list first.
      EvoPyProjectModel.getInstance(project).snapshot()
      assertThat(PythonInterpreterUpdater.getInstance(project).request(interpreter, PythonInterpreterUpdateRequest(packages = true, background = true))).isFalse()
    }
    finally {
      registry.remove(interpreter)
    }
  }

  @Test
  fun `a background update of the interpreter of a Python project is accepted`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(project)
    val interpreter = registry.addMockPythonInterpreter(pyProject, PythonMockSdk.create())
    try {
      pyProject.setPythonInterpreter(interpreter)
      // Accepted, and in a unit test it then does nothing, see `PythonInterpreterUpdater.setEnabledInTests`.
      assertThat(PythonInterpreterUpdater.getInstance(project).request(interpreter, PythonInterpreterUpdateRequest(packages = true, background = true))).isTrue()
    }
    finally {
      pyProject.setPythonInterpreter(null)
      registry.remove(interpreter)
    }
  }

  @Test
  fun `an interpreter stops being in use when the Python project moves to another one`(@TempDir root: Path): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(project)
    // Two mock SDKs at one path are one interpreter, so each one has its own path.
    val first = registry.addMockPythonInterpreter(pyProject, PythonMockSdk.create(root.resolve("first").toString()))
    val second = registry.addMockPythonInterpreter(pyProject, PythonMockSdk.create(root.resolve("second").toString()))
    try {
      pyProject.setPythonInterpreter(first)
      pyProject.setPythonInterpreter(second)
      val snapshot = EvoPyProjectModel.getInstance(project).snapshot()
      assertThat(snapshot.interpreters).doesNotContain(first)
      assertThat(snapshot.interpreters).contains(second)
    }
    finally {
      pyProject.setPythonInterpreter(null)
      registry.remove(first)
      registry.remove(second)
    }
  }
}
