// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.poetry

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.junit5Tests.framework.entryKeyOf
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.framework.env.RequiresPoetry
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.recreateEnvironmentFor
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.removeTestInterpreterFromOpenProjects
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.PythonBinary
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The poetry configurator makes the gone in-project environment of an entry in the `.venv` of the project. */
@PyEnvTestCase
@RequiresPoetry
internal class PoetryRecreateForEntryEnvTest {
  private val modulePathFixture = tempPathFixture(prefix = "poetry-recreate")
  private val pyProject by projectFixture().pyProjectFixture(modulePathFixture)

  @Test
  // The parameter makes the env extension create the environment, which sets the path of poetry.
  fun `a gone in-project poetry environment is made again in the project`(@PythonBinaryPath python: PythonBinary): Unit = timeoutRunBlocking(5.minutes) {
    val toml = pyProject.baseDir.resolve(PY_PROJECT_TOML)
    withContext(Dispatchers.IO) {
      toml.writeText("""
        [tool.poetry]
        name = "recreate"
        version = "0.1.0"
        description = ""
        authors = []
        package-mode = false

        [tool.poetry.dependencies]
        python = ">=3.8"
      """.trimIndent())
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(toml)
    }
    val key = entryKeyOf(pyProject, PoetryPyTool.getInstance().fusId, "in-project")

    val interpreter = recreateEnvironmentFor(POETRY_TOOL_ID, pyProject, key)
    try {
      assertNamedByEntry(interpreter, key)
      assertThat(pyProject.baseDir.resolve(".venv")).isDirectory()
    }
    finally {
      removeTestInterpreterFromOpenProjects(interpreter)
    }
  }

  /** The interpreter that comes out keeps the key that the entry names, so the pick does not rewrite the entry. */
  private fun assertNamedByEntry(interpreter: PythonInterpreter, key: PythonInterpreterKey) {
    assertThat(interpreter.ref)
      .describedAs("the environment is made where the entry names it, so its interpreter has the ref of the entry")
      .isEqualTo(key.ref)
    assertThat(interpreter.ownerDir).isEqualTo(key.ownerDir)
  }
}
