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
import org.junit.jupiter.api.Test

/**
 * PY-91629: `sub-project-a` references the sibling workspace member `sub-project-b` only through
 * PEP 621 `[project.optional-dependencies]` (an extra), never through `[project].dependencies`.
 * An extra is a declaration like any other, so the member must still become a module dependency;
 * otherwise `import sub_project_b` inside `sub-project-a` is reported as an unresolved import.
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-91629-uv-workspace-optional-dependency-on-member")
internal class UvWorkspaceOptionalDependencyOnMemberTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val f by pyProjectTomlSyncFixture(projectFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      // The root declares no dependency on any member, so its own dep list stays empty.
      ExpectedModule("pythonproject", contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("sub-project-a",
                     contentRoot = "sub-projects" / "sub-project-a",
                     deps = listOf("sub-project-b"),
                     sourceRoots = listOf("sub-projects" / "sub-project-a" / "src")),
      ExpectedModule("sub-project-b",
                     contentRoot = "sub-projects" / "sub-project-b",
                     sourceRoots = listOf("sub-projects" / "sub-project-b" / "src")),
    )
  }
}
