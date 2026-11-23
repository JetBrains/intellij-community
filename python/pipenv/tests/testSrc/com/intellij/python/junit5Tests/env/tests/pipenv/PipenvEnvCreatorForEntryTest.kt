// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.pipenv

import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.intellij.python.community.impl.pipenv.PipEnvBundle
import com.intellij.python.community.impl.pipenv.PipEnvPyTool
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
import com.jetbrains.python.sdk.configuration.PIPENV_TOOL_ID
import kotlin.io.path.pathString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Which gone environments of an interpreter library entry the pipenv configurator makes again, see
 * `PyProjectSdkConfigurationExtension.prepareSdkCreatorFor`. The creator is asked for, never called, so no tool runs.
 */
@Subsystems.Interpreters
@Components.Pipenv
@Layers.Functional
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
internal class PipenvEnvCreatorForEntryTest {
  private val projectPath = tempPathFixture(prefix = "project")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val module = projectFixture.pyModuleFixture(projectPath, addPathToSourceRoot = true)
  private val pyProject = module.pyProjectFixture()

  private val pipenv: FusId get() = PipEnvPyTool.getInstance().fusId

  @Test
  fun `pipenv makes the in-project environment`(): Unit = timeoutRunBlocking {
    assertThat(intentionOf(pipenv, ".venv")).isEqualTo(PipEnvBundle.message("sdk.recreate.pipenv.environment"))
  }

  @Test
  fun `pipenv makes an absolute environment where the entry names it`(): Unit = timeoutRunBlocking {
    val elsewhere = projectPath.get().resolveSibling("virtualenvs").resolve("app-Xa2b3c4d").resolve("bin").resolve("python")
    assertThat(intentionOf(pipenv, elsewhere.pathString))
      .describedAs("pipenv is told the exact directory, so an absolute home is made where the entry names it")
      .isEqualTo(PipEnvBundle.message("sdk.recreate.pipenv.environment"))
  }

  @Test
  fun `pipenv does not make another relative environment`(): Unit = timeoutRunBlocking {
    assertThat(intentionOf(pipenv, "envs/other")).isNull()
  }

  private suspend fun intentionOf(manager: FusId, envRef: String): String? =
    recreateIntentionOf(PIPENV_TOOL_ID, pyProject.get(), entryKeyOf(pyProject.get(), manager, envRef))
}
