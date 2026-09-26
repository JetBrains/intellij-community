// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.packaging.PyPackageName
import org.junit.jupiter.api.Test

/**
 * PY-91189: a uv workspace whose root `pyproject.toml` has **no `[project]` table** at all, only
 * `[tool.uv.workspace]` and `[dependency-groups]`. uv calls this a *virtual* project: a legitimate
 * workspace root that is not itself a distributable package.
 *
 * The root must still be recognized as a project (taking its name from the containing directory, since
 * there is no `[project].name`), so that its `members` become workspace members sharing the root's
 * environment. Without virtual-project support the root `pyproject.toml` yields no project at all, so the
 * root directory keeps the plain (non-pyproject) module it already had and `[tool.uv.workspace]` is never
 * read — which is what left every member with its own venv in the ticket.
 *
 * Both members use the flat layout, so neither contributes a `src` source root.
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-91189-uv-workspace-root-without-project")
internal class UvWorkspaceRootWithoutProjectTableTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val f by pyProjectTomlSyncFixture(projectFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    // The virtual root has no `[project].name`, so its module is named after its directory, which under a
    // temp-dir project fixture is generated per run. It also has no `[project].dependencies`, hence no deps;
    // the load-bearing part of the expectation is the default `type = PYPROJECT`, i.e. the root became a
    // pyproject-based module with a `PyProjectTomlWorkspaceEntity`. Without virtual-project support the root
    // stays the implicit Python module `PyDefaultTestApplication` created (named `.`, not pyproject-based).
    f.assertProjectStructure(
      ExpectedModule(PyPackageName.normalizeProjectName(f.root.name), contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("pkg-core", contentRoot = "pkg_core"),
      ExpectedModule("pkg-app", contentRoot = "pkg_app", deps = listOf("pkg-core")),
    )
  }
}
