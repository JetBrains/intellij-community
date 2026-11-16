// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.conda

import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.community.impl.conda.PyCondaBundle
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
import com.jetbrains.python.sdk.configuration.CONDA_TOOL_ID
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Which gone environments of an interpreter library entry the conda configurator makes again, see
 * `PyProjectSdkConfigurationExtension.prepareSdkCreatorFor`. The creator is asked for, never called, so no tool runs.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
internal class CondaEnvCreatorForEntryTest {
  private val projectPath = tempPathFixture(prefix = "project")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val module = projectFixture.pyModuleFixture(projectPath, addPathToSourceRoot = true)
  private val pyProject = module.pyProjectFixture()

  private val conda: FusId get() = CondaPyTool.getInstance().fusId

  @Test
  fun `conda makes the named environment of the entry`(): Unit = timeoutRunBlocking(30.seconds) {
    assertThat(intentionOf(conda, "myenv")).isEqualTo(PyCondaBundle.message("sdk.recreate.conda.environment", "myenv"))
  }

  @Test
  fun `conda makes the prefix environment in the form of the entry`(): Unit = timeoutRunBlocking(30.seconds) {
    assertThat(intentionOf(conda, "~/no-such-dir/.conda"))
      .describedAs("the prefix keeps the form of the entry, so the interpreter that comes out has the ref of the entry")
      .isEqualTo(PyCondaBundle.message("sdk.recreate.conda.environment", "~/no-such-dir/.conda"))
  }

  @Test
  fun `conda does not make a prefix that is there`(): Unit = timeoutRunBlocking(30.seconds) {
    assertThat(intentionOf(conda, projectPath.get().pathString)).isNull()
  }

  private suspend fun intentionOf(manager: FusId, envRef: String): String? =
    recreateIntentionOf(CONDA_TOOL_ID, pyProject.get(), entryKeyOf(pyProject.get(), manager, envRef))
}
