// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.pipenv

import com.intellij.python.community.impl.pipenv.PipEnvPyTool
import com.intellij.python.junit5Tests.framework.entryKeyOf
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.recreateEnvironmentFor
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.removeTestInterpreterFromOpenProjects
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.configuration.PIPENV_TOOL_ID
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.pipenv.runPipEnv
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The pipenv configurator makes the gone in-project environment of an entry in the `.venv` of the project. */
@PyEnvTestCase
internal class PipenvRecreateForEntryEnvTest {
  private val modulePathFixture = tempPathFixture(prefix = "pipenv-recreate")
  private val pyProject by projectFixture().pyProjectFixture(modulePathFixture)

  @Test
  // The parameter makes the env extension create the environment, which sets the path of pipenv.
  fun `a gone in-project pipenv environment is made again in the project`(@PythonBinaryPath python: PythonBinary): Unit = timeoutRunBlocking(5.minutes) {
    val baseDir = pyProject.baseDir
    withContext(Dispatchers.IO) { baseDir.resolve("Pipfile").writeText("[packages]\n") }
    val key = entryKeyOf(pyProject, PipEnvPyTool.getInstance().fusId, ".venv")

    val interpreter = recreateEnvironmentFor(PIPENV_TOOL_ID, pyProject, key)
    try {
      assertNamedByEntry(interpreter, key)
      assertThat(baseDir.resolve(".venv")).isDirectory()
    }
    finally {
      try {
        removeTestInterpreterFromOpenProjects(interpreter)
      }
      finally {
        runPipEnv(baseDir.toFileSystem(), baseDir, "--rm")
      }
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
