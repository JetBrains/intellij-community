// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.mcp.environment

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test

class PythonEnvironmentResultTest {

  @Test
  fun `noInterpreterConfigured result has null interpreter fields`() {
    val result = PythonEnvironmentMcpToolset.GetPythonEnvironmentResult(
      noInterpreterConfigured = "No Python interpreter configured for: foo.py."
    )

    result.pythonVersion.shouldBeNull()
    result.environmentType.shouldBeNull()
    result.executablePath.shouldBeNull()
    result.environmentPath.shouldBeNull()
    result.packageManager.shouldBeNull()
    result.noInterpreterConfigured shouldBe "No Python interpreter configured for: foo.py."
  }

  @Test
  fun `successful result has null noInterpreterConfigured`() {
    val result = PythonEnvironmentMcpToolset.GetPythonEnvironmentResult(
      pythonVersion = "Python 3.11.4",
      environmentType = "venv",
      executablePath = "/project/.venv/bin/python",
      environmentPath = "/project/.venv",
      packageManager = "uv",
    )

    result.noInterpreterConfigured.shouldBeNull()
    result.pythonVersion shouldBe "Python 3.11.4"
    result.environmentType shouldBe "venv"
    result.executablePath shouldBe "/project/.venv/bin/python"
    result.environmentPath shouldBe "/project/.venv"
    result.packageManager shouldBe "uv"
  }

  @Test
  fun `noInterpreterConfigured ends with configure suffix when PyCharm can set up interpreter`() {
    val result = PythonEnvironmentMcpToolset.GetPythonEnvironmentResult(
      noInterpreterConfigured = "No Python interpreter configured for: src/main.py. " +
                                "PyCharm can attach the existing virtual environment detected for this module " +
                                "— ${CONFIGURE_PYTHON_INTERPRETER_SUFFIX}"
    )

    result.noInterpreterConfigured.shouldNotBeNull()
    result.noInterpreterConfigured shouldEndWith CONFIGURE_PYTHON_INTERPRETER_SUFFIX
  }

  @Test
  fun `noInterpreterConfigured for missing tool does not contain configure suffix`() {
    val result = PythonEnvironmentMcpToolset.GetPythonEnvironmentResult(
      noInterpreterConfigured = "No Python interpreter configured for: src/main.py. " +
                                "PyCharm expects this project to use uv, but uv is not installed."
    )

    result.noInterpreterConfigured.shouldNotBeNull()
    val hint = result.noInterpreterConfigured
    assert(!hint.contains(CONFIGURE_PYTHON_INTERPRETER_SUFFIX)) {
      "WillInstallTool hint must not tell agent to call configure_python_interpreter, got: $hint"
    }
  }

  @Test
  fun `system python result has null environmentPath`() {
    val result = PythonEnvironmentMcpToolset.GetPythonEnvironmentResult(
      pythonVersion = "Python 3.12.0",
      environmentType = "system",
      executablePath = "/usr/bin/python3",
      environmentPath = null,
      packageManager = "pip",
    )

    result.environmentPath.shouldBeNull()
    result.noInterpreterConfigured.shouldBeNull()
  }
}
