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
 * PY-91089: the root `pyproject.toml` declares `tool.uv.workspace` as an array of tables
 * (`[[tool.uv.workspace]]`) instead of a table. The malformed value must NOT abort the model sync:
 * both members are still discovered as modules (only their workspace grouping — and the deps derived
 * from it — is lost, since the workspace declaration is unusable).
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-91089-uv-workspace-array-workspace")
internal class UvWorkspaceArrayWorkspaceTest {
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
      ExpectedModule("orgwiki-test", contentRoot = "orgwiki-test", sourceRoots = listOf("orgwiki-test" / "src")),
    )
  }
}
