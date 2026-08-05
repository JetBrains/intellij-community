// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.showcase

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.pyproject.model.api.ModuleSdkState
import com.intellij.python.pyproject.model.api.autoConfigureSdkExistingOnly
import com.intellij.python.pyproject.model.api.configureSdkIfNeeded
import com.intellij.python.pyproject.model.api.getModuleSdkState
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.moduleInProjectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication
import com.jetbrains.python.junit5.framework.pyExternalSystemProjectFixture
import com.jetbrains.python.junit5.framework.util.completeBasicAtProjectFile
import com.jetbrains.python.testDataPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Duration.Companion.minutes

/**
 * PY-91376: in a uv workspace, an SDK-provided module (here the stdlib `json`) imported from a workspace
 * *member* must resolve, given the workspace root already has the interpreter. Members inherit the root's SDK
 * via the `SameAs` mechanism, which the product drives from `configureSdkAutomatically` after a model rebuild.
 *
 * The mock-SDK fixture assigns the interpreter to the root module ("workspace-root"). This test then mirrors
 * the product's per-module SDK propagation and asserts that the member "core" ends up with an SDK and resolves
 * `json`.
 */
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath($$"$CONTENT_ROOT/../testData/junit5/showcase/py91376workspace")
@PyCodeInsightTestApplication
@Subsystems.CodeInsight
@Layers.Functional
internal class PyUvWorkspaceMemberSdkTest(val project: Project) {

  companion object {
    private val TEST_DATA = Path.of(testDataPath) / "junit5/showcase/py91376workspace"

    @JvmField
    val projectFixture = pyExternalSystemProjectFixture(TEST_DATA)

    // The mock SDK is assigned to the workspace root module.
    @JvmField
    val moduleFixture = projectFixture.moduleInProjectFixture("workspace-root")
  }

  @Test
  fun `member inherits workspace root sdk and resolves stdlib`(fixture: CodeInsightTestFixture): Unit =
    timeoutRunBlocking(2.minutes) {
      val modules = project.modules.associateBy { it.name }
      assertThat(modules.keys).contains("workspace-root", "core", "app-a")
      val core = modules.getValue("core")

      // Mirror ModulesSdkConfigurator.configureSdkAutomatically (multi-module branch): each module is
      // configured "existing only" — for a member this resolves to SameAs(root), inheriting the root's SDK.
      for (module in ModuleManager.getInstance(project).modules) {
        module.configureSdkIfNeeded { autoConfigureSdkExistingOnly() }
      }

      assertThat(core.getModuleSdkState())
        .describedAs("member 'core' must inherit the workspace root SDK")
        .isInstanceOf(ModuleSdkState.HasSdk::class.java)

      // End-to-end: the stdlib import in the member resolves (no "No module named 'json'").
      assertThat(fixture.completeBasicAtProjectFile("packages/core/src/core/my_file.py", "core"))
        .contains("dumps", "loads")
    }
}
