// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.conda

import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.python.junit5Tests.framework.entryKeyOf
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.recreateEnvironmentFor
import com.intellij.python.sdk.backend.removeTestInterpreterFromOpenProjects
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.conda.savePythonCondaPath
import com.jetbrains.python.sdk.configuration.CONDA_TOOL_ID
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The conda configurator makes the gone prefix environment of an entry at that prefix, from the `environment.yml` of the
 * project, whatever name the file states.
 */
@Subsystems.Interpreters
@Components.Conda
@Layers.Functional
@PyEnvTestCaseWithConda
internal class CondaRecreateForEntryEnvTest {
  private val modulePathFixture = tempPathFixture(prefix = "conda-recreate")
  private val pyProject by projectFixture().pyProjectFixture(modulePathFixture)

  @Test
  fun `a gone conda prefix environment is made again at the prefix of the entry`(@CondaEnv condaEnv: PyCondaEnv): Unit = timeoutRunBlocking(10.minutes) {
    savePythonCondaPath(Path.of(condaEnv.fullCondaPathOnTarget))
    val baseDir = pyProject.baseDir
    withContext(Dispatchers.IO) {
      baseDir.resolve("environment.yml").writeText("""
        name: another-name
        dependencies:
          - python
      """.trimIndent())
    }
    val prefix = baseDir.resolve(".conda-env")
    val key = entryKeyOf(pyProject, CondaPyTool.getInstance().fusId, prefix.pathString)

    val interpreter = recreateEnvironmentFor(CONDA_TOOL_ID, pyProject, key)
    try {
      assertNamedByEntry(interpreter, key)
      assertThat(prefix).isDirectory()
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
