// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.hatch

import com.intellij.python.hatch.HatchPyTool
import com.intellij.python.hatch.PyHatchBundle
import com.intellij.python.hatch.impl.HATCH_TOOL_ID
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
 * Which gone environments of an interpreter library entry the hatch configurator makes again, see
 * `PyProjectSdkConfigurationExtension.prepareSdkCreatorFor`. The creator is asked for, never called, so no tool runs.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
internal class HatchEnvCreatorForEntryTest {
  private val projectPath = tempPathFixture(prefix = "project")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val module = projectFixture.pyModuleFixture(projectPath, addPathToSourceRoot = true)
  private val pyProject = module.pyProjectFixture()

  private val hatch: FusId get() = HatchPyTool.getInstance().fusId

  @Test
  fun `hatch makes the environment of the entry by its name`(): Unit = timeoutRunBlocking(30.seconds) {
    assertThat(intentionOf(hatch, "test")).isEqualTo(PyHatchBundle.message("sdk.recreate.hatch.environment", "test"))
  }

  private suspend fun intentionOf(manager: FusId, envRef: String): String? =
    recreateIntentionOf(HATCH_TOOL_ID, pyProject.get(), entryKeyOf(pyProject.get(), manager, envRef))
}
