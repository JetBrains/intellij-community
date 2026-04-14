// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pyproject.model.testplan

import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Test

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/uv_workspace_codeinsightg_check")
internal class UvWorkspaceCodeInsightCheckTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  /**
   * The root `my-uv-monorepo` declares `package_a` in `[tool.uv.sources]` with `workspace = true`,
   * but does not list it in `[project].dependencies`. Module dependencies are resolved from
   * `[project].dependencies` only, so the root has no module-level dep on `package_a`.
   */
  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("my-uv-monorepo", contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("package_a", contentRoot = "packages/package_a"),
      ExpectedModule("package_b", contentRoot = "packages/package_b", deps = listOf("package_a")),
    )
  }
}