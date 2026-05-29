// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.SEP
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Test

/**
 * When the parent workspace and the child project both declare the same `tool.uv.sources` entry,
 * the child's declaration wins. Here `frontend` overrides parent's `mylib = { workspace = true }`
 * (which would resolve to the `libs/mylib` workspace member) with `mylib = { path = "../../external_mylib" }`,
 * so `frontend`'s only dep must be the external project, not the workspace sibling.
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/8840337-uv-workspace-child-overrides-parent")
internal class MonorepoUvChildOverridesParentSourceTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }

  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun childTableWinsOverParentTable(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("monorepo-root", contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("mylib", contentRoot = "libs${SEP}mylib"),
      ExpectedModule("external_mylib", contentRoot = "external_mylib"),
      ExpectedModule("frontend", contentRoot = "apps${SEP}frontend", deps = listOf("external_mylib")),
    )
  }
}
