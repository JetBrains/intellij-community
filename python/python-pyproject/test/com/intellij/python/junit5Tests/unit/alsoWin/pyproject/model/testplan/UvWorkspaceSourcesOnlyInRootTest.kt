// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.div
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Test

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-89861-uv-workspave-tool.uv.sources-only-in-root")
internal class UvWorkspaceSourcesOnlyInRootTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  /**
   * PY-89861: The root `8840337-uv-workspave` declares `myorg-core` in `[tool.uv.sources]`
   * with `workspace = true`. The workspace member `myorg-frontend` depends on `myorg-core`
   * via its own `[project].dependencies` and it is unnecessary to add `[tool.uv.sources]` in
   * `myorg-frontend` pyproject.toml.
   */
  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("8840337-uv-workspave", contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("myorg-frontend", contentRoot = "apps" / "frontend", deps = listOf("myorg-core"), sourceRoots = listOf("apps" / "frontend" / "src")),
      ExpectedModule("myorg-core", contentRoot = "libs" / "core", sourceRoots = listOf("libs" / "core" / "src")),
    )
  }
}
