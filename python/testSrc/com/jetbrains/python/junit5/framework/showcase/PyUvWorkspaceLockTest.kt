// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.showcase

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication
import com.jetbrains.python.junit5.framework.pyExternalSystemProjectFixture
import com.jetbrains.python.testDataPath
import com.jetbrains.python.uv.findUvLock
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

/**
 * PY-92193: a uv workspace keeps one `uv.lock`, and it keeps it at the workspace root.
 *
 * Every uv question about a member is therefore answered from that root. Answered from the member alone, the lock of
 * a synchronized workspace looked absent, the uv configurator declined the member, and the fallback configurator then
 * built the environment in the member's own directory. `uv venv` obeyed that directory and `uv sync` hoisted to the
 * workspace root, so one workspace ended with two environments.
 */
@TestClassInfo(Repository.PY_COMMUNITY)
@PyCodeInsightTestApplication
@Subsystems.CodeInsight
@Layers.Functional
internal class PyUvWorkspaceLockTest(val project: Project) {

  companion object {
    private val TEST_DATA = Path.of(testDataPath) / "junit5/showcase/py92193uvworkspacelock"

    @JvmField
    val projectFixture = pyExternalSystemProjectFixture(TEST_DATA)
  }

  @Test
  fun `a member reads the lock of its workspace root`(): Unit = timeoutRunBlocking(2.minutes) {
    val modules = project.modules.associateBy { it.name }
    assertThat(modules.keys).contains("workspace-root", "core")
    val rootLock = findUvLock(modules.getValue("workspace-root"))
    assertThat(rootLock)
      .describedAs("the workspace root holds the lock")
      .isNotNull()
    assertThat(findUvLock(modules.getValue("core")))
      .describedAs("the member is answered by the same lock, not by its own directory")
      .isEqualTo(rootLock)
  }
}
