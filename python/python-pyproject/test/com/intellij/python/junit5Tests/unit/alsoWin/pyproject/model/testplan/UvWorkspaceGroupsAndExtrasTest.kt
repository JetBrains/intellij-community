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
import org.junit.jupiter.api.Test

/**
 * PY-91376: uv workspace where a member declares `[project.optional-dependencies]` and nested
 * `[dependency-groups]` alongside plain `[project.dependencies]`, and the root uses `package = false`,
 * `environments`, and an `[[tool.uv.index]]` array-of-tables.
 *
 * Verifies the workspace model still assembles correctly: every member is recognized as a member of
 * the single `workspace-root` workspace (which is what lets members share the root's SDK), and
 * member-to-member deps come only from `[project.dependencies]`.
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-91376-uv-workspace-groups-and-extras")
internal class UvWorkspaceGroupsAndExtrasTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val f by pyProjectTomlSyncFixture(projectFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("workspace-root", contentRoot = ".", deps = listOf("core", "app-a", "app-b"), sourceRoots = listOf(".")),
      ExpectedModule("core", contentRoot = "packages" / "core", sourceRoots = listOf("packages" / "core" / "src")),
      ExpectedModule("app-a",
                     contentRoot = "projects" / "app-a",
                     deps = listOf("core"),
                     sourceRoots = listOf("projects" / "app-a" / "src")),
      ExpectedModule("app-b",
                     contentRoot = "projects" / "app-b",
                     deps = listOf("core"),
                     sourceRoots = listOf("projects" / "app-b" / "src")),
    )
  }
}
