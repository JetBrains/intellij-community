// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.poetry

import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.community.impl.poetry.backend.PyPoetryBundle
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.junit5Tests.framework.entryKeyOf
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.recreateIntentionOf
import com.intellij.python.pytools.common.FusId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlin.time.Duration.Companion.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Which gone environments of an interpreter library entry the poetry configurator makes again, see
 * `PyProjectSdkConfigurationExtension.prepareSdkCreatorFor`. The creator is asked for, never called, so no tool runs.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
internal class PoetryEnvCreatorForEntryTest {
  private val projectPath = tempPathFixture(prefix = "project")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val module = projectFixture.pyModuleFixture(projectPath, addPathToSourceRoot = true)
  private val pyProject = module.pyProjectFixture()

  private val poetry: FusId get() = PoetryPyTool.getInstance().fusId

  @Test
  fun `poetry makes the in-project environment`(): Unit = timeoutRunBlocking(30.seconds) {
    assertThat(intentionOf(poetry, "in-project")).isEqualTo(PyPoetryBundle.message("sdk.recreate.poetry.in.project.environment"))
  }

  @Test
  fun `poetry makes the cache environment of a Python version`(): Unit = timeoutRunBlocking(30.seconds) {
    assertThat(intentionOf(poetry, "py3.13")).isEqualTo(PyPoetryBundle.message("sdk.recreate.poetry.cache.environment", "3.13"))
  }

  @Test
  fun `poetry does not make an environment named by a path`(): Unit = timeoutRunBlocking(30.seconds) {
    assertThat(intentionOf(poetry, "envs/other")).isNull()
  }

  private suspend fun intentionOf(manager: FusId, envRef: String): String? =
    recreateIntentionOf(POETRY_TOOL_ID, pyProject.get(), entryKeyOf(pyProject.get(), manager, envRef))
}
