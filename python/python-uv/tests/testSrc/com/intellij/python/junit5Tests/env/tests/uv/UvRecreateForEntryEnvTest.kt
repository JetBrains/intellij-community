// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.uv

import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.intellij.python.junit5Tests.framework.entryKeyOf
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.RequiresUv
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.recreateEnvironmentFor
import com.intellij.python.sdk.backend.removeTestInterpreterFromOpenProjects
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.uv.common.UV_TOOL_ID
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlin.time.Duration.Companion.minutes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The uv configurator makes the gone environment of an entry at the home that the entry names, not in `.venv`. */
@Subsystems.Interpreters
@Components.Uv
@Layers.Functional
@PyEnvTestCase
@RequiresUv
internal class UvRecreateForEntryEnvTest {
  private val modulePathFixture = tempPathFixture(prefix = "uv-recreate")
  private val pyProject by projectFixture().pyProjectFixture(modulePathFixture)

  @Test
  fun `a gone uv environment is made again in the folder of the entry`(): Unit = timeoutRunBlocking(5.minutes) {
    val key = entryKeyOf(pyProject, UvPyTool.getInstance().fusId, ".my_custom_venv_folder")

    val interpreter = recreateEnvironmentFor(UV_TOOL_ID, pyProject, key)
    try {
      assertNamedByEntry(interpreter, key)
      assertThat(pyProject.baseDir.resolve(".my_custom_venv_folder")).isDirectory()
      assertThat(pyProject.baseDir.resolve(".venv")).doesNotExist()
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
