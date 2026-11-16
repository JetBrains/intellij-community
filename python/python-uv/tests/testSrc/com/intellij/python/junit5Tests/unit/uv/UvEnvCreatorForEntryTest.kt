// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.uv

import com.intellij.python.junit5Tests.framework.entryKeyOf
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.recreateIntentionOf
import com.intellij.python.pytools.common.FusId
import com.intellij.python.sdk.backend.PIP_MANAGER
import com.intellij.python.uv.backend.PyUvBundle
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.uv.common.UV_TOOL_ID
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlin.time.Duration.Companion.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Which gone environments of an interpreter library entry the uv configurator makes again, see
 * `PyProjectSdkConfigurationExtension.prepareSdkCreatorFor`. The creator is asked for, never called, so no tool runs.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
internal class UvEnvCreatorForEntryTest {
  private val projectPath = tempPathFixture(prefix = "project")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val module = projectFixture.pyModuleFixture(projectPath, addPathToSourceRoot = true)
  private val pyProject = module.pyProjectFixture()

  private val uv: FusId get() = UvPyTool.getInstance().fusId

  @Test
  fun `uv makes the environment at the home of the entry`(): Unit = timeoutRunBlocking(30.seconds) {
    assertThat(intentionOf(uv, ".my_env")).isEqualTo(PyUvBundle.message("sdk.recreate.uv.environment", ".my_env"))
  }

  @Test
  fun `uv does not make an environment of another manager`(): Unit = timeoutRunBlocking(30.seconds) {
    assertThat(intentionOf(PIP_MANAGER, ".my_env")).isNull()
  }

  private suspend fun intentionOf(manager: FusId, envRef: String): String? =
    recreateIntentionOf(UV_TOOL_ID, pyProject.get(), entryKeyOf(pyProject.get(), manager, envRef))
}
