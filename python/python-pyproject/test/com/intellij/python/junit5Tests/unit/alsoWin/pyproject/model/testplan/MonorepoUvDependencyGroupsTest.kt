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
 * uv workspace with dependencies declared as dependency groups.
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/uv_workspace_dep_groups")
internal class MonorepoUvDependencyGroupsTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }

  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun testDependencyGroupBasedDeps(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("pythonproject", contentRoot = ".", sourceRoots = listOf("."), deps = listOf("sub-project-a", "sub-project-b")),
      ExpectedModule("sub-project-a", contentRoot = "sub-projects${SEP}sub-project-a"),
      ExpectedModule("sub-project-b", contentRoot = "sub-projects${SEP}sub-project-b", deps = listOf("sub-project-a")),
    )
  }
}
