// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.idea.TestFor
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.div
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/hatch_path_dependency")
internal class HatchPathDependencyTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val f by pyProjectTomlSyncFixture(projectFixture)

  @Test
  @TestFor(issues = ["PY-90798"])
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("my-hatch-monorepo", contentRoot = ".", deps = listOf("package_a", "package_b"), sourceRoots = listOf(".")),
      ExpectedModule("package_a", contentRoot = "packages" / "package_a"),
      ExpectedModule("package_b", contentRoot = "packages" / "package_b", deps = listOf("package_a")),
    )
  }
}
