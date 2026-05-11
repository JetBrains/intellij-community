// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@PyDefaultTestApplication
@TestClassInfo(contentRootPath ="python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/hatch_workspace_path_dependency")
internal class HatchWorkspacePathDependencyTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    // PY-87339 Hatch monorepo local dependencies are not show in settings
    // PY-86924 Hatch workspace members are not handled correctly
    assertThrows<AssertionError> {
      f.assertProjectStructure(
        ExpectedModule("my-hatch-monorepo", contentRoot = ".", deps = listOf("package_a", "package_b")),
        ExpectedModule("package_a", contentRoot = "packages/package_a"),
        ExpectedModule("package_b", contentRoot = "packages/package_b", deps = listOf("package_a")),
      )
    }
  }
}
