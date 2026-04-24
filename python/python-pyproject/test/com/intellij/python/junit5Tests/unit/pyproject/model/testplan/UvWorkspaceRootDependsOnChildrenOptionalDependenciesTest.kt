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
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/uv_workspace_root_depends_on_children_optional_dependencies")
internal class UvWorkspaceRootDependsOnChildrenOptionalDependenciesTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("pythonproject", contentRoot = ".", deps = listOf("sub-project-a", "sub-project-b"), sourceRoots = listOf(".", "src")),
      ExpectedModule("sub-project-a", contentRoot = "sub-projects/sub-project-a", sourceRoots = listOf("sub-projects/sub-project-a/src")),
      ExpectedModule("sub-project-b", contentRoot = "sub-projects/sub-project-b", sourceRoots = listOf("sub-projects/sub-project-b/src")),
    )
  }
}