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

/**
 * PY-91089: the root `pyproject.toml` declares `tool.uv.sources` as an array of tables
 * (`[[tool.uv.sources]]`) instead of a table (`[tool.uv.sources]`) — a common double-bracket
 * typo. `uv lock`/`uv sync` still succeed because uv ignores non-member files, so the project
 * looks fine outside the IDE. The malformed value must NOT abort the model sync: every
 * workspace member — including the freshly added `orgwiki-test` — must still be discovered as
 * a module.
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-91089-uv-workspace-array-sources")
internal class UvWorkspaceArraySourcesTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("orgwiki", contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("orgwiki-core", contentRoot = "orgwiki-core", sourceRoots = listOf("orgwiki-core" / "src")),
      ExpectedModule("orgwiki-test", contentRoot = "orgwiki-test", deps = listOf("orgwiki-core"), sourceRoots = listOf("orgwiki-test" / "src")),
    )
  }
}
