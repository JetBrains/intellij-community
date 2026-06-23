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

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/uv_workspace_two_folders_with_members")
internal class UvWorkspaceTwoFoldersWithMembersTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("workspace", contentRoot = ".", deps = listOf("nebula", "quasar", "abyss"), sourceRoots = listOf(".")),
      ExpectedModule("nebula", contentRoot = "cosmos" / "nebula", sourceRoots = listOf("cosmos" / "nebula" / "src")),
      ExpectedModule("quasar", contentRoot = "cosmos" / "quasar", sourceRoots = listOf("cosmos" / "quasar" / "src")),
      ExpectedModule("abyss", contentRoot = "ocean" / "abyss", deps = listOf("nebula", "quasar"), sourceRoots = listOf("ocean" / "abyss" / "src")),
    )
  }
}
