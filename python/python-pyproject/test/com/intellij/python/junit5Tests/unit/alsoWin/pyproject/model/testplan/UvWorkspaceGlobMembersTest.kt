// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.div
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.PYTHON
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * PY-89677 (follow-up comment): a uv workspace whose root `pyproject.toml` has no `[project]`
 * section — a non-package root (`[tool.uv] package = false`) — declares its members with glob
 * patterns (`apps` and `packages` wildcards), and keeps `[tool.uv.sources]` only in the root.
 * The app member `apps/app` depends on `packages/package` (`dependencies = ["package"]`), so
 * both members must be discovered as modules and the `app` -> `package` dependency set up.
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-89677-uv-workspace-glob-members")
internal class UvWorkspaceGlobMembersTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    // PY-89677 uv workspace: intra-workspace `{ workspace = true }` dependency is not set up as a
    // module dependency when the root is a non-package root (no `[project]`) with glob members.
    // Both members are discovered with correct source roots, but the `app` -> `package` dependency
    // edge is missing. Drop the `assertThrows` wrapper once the model resolves it.
    assertThrows<AssertionError> {
      f.assertProjectStructure(
        ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
        ExpectedModule("package", contentRoot = "packages" / "package", sourceRoots = listOf("packages" / "package" / "src")),
        ExpectedModule("app", contentRoot = "apps" / "app", deps = listOf("package"), sourceRoots = listOf("apps" / "app" / "src")),
      )
    }
  }
}
