// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.hatch

import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.hatch.HatchPyTool
import com.intellij.python.hatch.impl.HATCH_TOOL_ID
import com.intellij.python.junit5Tests.framework.entryKeyOf
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.recreateEnvironmentFor
import com.intellij.python.junit5Tests.framework.resolvePythonTool
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pytools.backend.setCustomExecutablePath
import com.intellij.python.sdk.backend.removeTestInterpreterFromOpenProjects
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The hatch configurator makes the gone environment of an entry by its name. The project places that environment in
 * itself with the `path` option, so the test leaves nothing in the data directory of hatch.
 */
@Subsystems.Interpreters
@Components.Hatch
@Layers.Functional
@PyEnvTestCase
internal class HatchRecreateForEntryEnvTest {
  private val modulePathFixture = tempPathFixture(prefix = "hatch-recreate")
  private val pyProject by projectFixture().pyProjectFixture(modulePathFixture)

  @Test
  fun `a gone hatch environment is made again by its name`(@PythonBinaryPath python: PythonBinary): Unit = timeoutRunBlocking(5.minutes) {
    val hatch = VirtualEnvReader().resolvePythonHomeFromPythonBinary(python).resolvePythonTool("hatch")
    val hatchTool = HatchPyTool.getInstance()
    val eelDescriptor = hatch.getEelDescriptor()
    val previousHatch = hatchTool.getCustomExecutablePath(eelDescriptor)
    hatchTool.setCustomExecutablePath(eelDescriptor, hatch)
    val toml = pyProject.baseDir.resolve(PY_PROJECT_TOML)
    withContext(Dispatchers.IO) {
      toml.writeText("""
        [project]
        name = "recreate"
        version = "0.1.0"

        [tool.hatch.envs.test]
        path = ".hatch/test"
      """.trimIndent())
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(toml)
    }
    val key = entryKeyOf(pyProject, HatchPyTool.getInstance().fusId, "test")

    try {
      val interpreter = recreateEnvironmentFor(HATCH_TOOL_ID, pyProject, key)
      try {
        assertNamedByEntry(interpreter, key)
        assertThat(pyProject.baseDir.resolve(".hatch").resolve("test")).isDirectory()
      }
      finally {
        removeTestInterpreterFromOpenProjects(interpreter)
      }
    }
    finally {
      hatchTool.setCustomExecutablePath(eelDescriptor, previousHatch)
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
