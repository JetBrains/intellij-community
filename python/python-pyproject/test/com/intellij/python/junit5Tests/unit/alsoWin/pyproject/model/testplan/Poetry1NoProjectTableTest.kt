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
import org.junit.jupiter.api.Test

/**
 * PY-91765: Poetry 1.x predates PEP 621, so the `pyproject.toml` it generates has **no `[project]` table**
 * at all — `name`, `version`, `authors` and `packages` live under `[tool.poetry]`, and dependencies live in
 * the `[tool.poetry.dependencies]` table rather than a PEP 621 array.
 *
 * Such a file must still yield a project, named after `[tool.poetry].name`. Both subprojects therefore
 * become pyproject modules with their `src` source root, and `poetry1app`'s `[tool.poetry.dependencies]`
 * path entry becomes a module dependency on `poetry1lib`.
 *
 * Without the `[tool.poetry]` fallback neither `pyproject.toml` produces a project, so no module is
 * detected and everything stays under the single implicit root module — the symptom in the ticket.
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-91765-poetry1-no-project-table")
internal class Poetry1NoProjectTableTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val f by pyProjectTomlSyncFixture(projectFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("poetry1lib", contentRoot = "poetry1lib", sourceRoots = listOf("poetry1lib" / "src")),
      ExpectedModule("poetry1app", contentRoot = "poetry1app", deps = listOf("poetry1lib"),
                     sourceRoots = listOf("poetry1app" / "src")),
    )
  }
}
