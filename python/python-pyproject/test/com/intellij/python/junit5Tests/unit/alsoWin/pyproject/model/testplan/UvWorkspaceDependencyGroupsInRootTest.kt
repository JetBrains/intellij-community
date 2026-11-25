// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.openapi.project.Project
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.div
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.python.pyproject.model.internal.workspaceBridge.PY_PROJECT_MODEL_OWNS_MODULES_KEY
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal abstract class UvWorkspaceDependencyGroupsInRootTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    // PY-89205 uv workspace: Dependencies which are defined in dependency groups are not shown in Project Structure settings
    assertThrows<AssertionError> {
      f.assertProjectStructure(
        ExpectedModule("pythonproject",
                       contentRoot = ".",
                       deps = listOf("sub-project-a", "sub-project-b", "sub-project-c"),
                       sourceRoots = emptyList()),
        ExpectedModule("sub-project-a",
                       contentRoot = "sub-projects" / "sub-project-a",
                       deps = listOf("sub-project-b", "sub-project-c"),
                       sourceRoots = emptyList()),
        ExpectedModule("sub-project-b",
                       contentRoot = "sub-projects" / "sub-project-b",
                       deps = listOf("sub-project-c"),
                       sourceRoots = emptyList()),
        ExpectedModule("sub-project-c", contentRoot = "sub-projects" / "sub-project-c", deps = listOf("."), sourceRoots = emptyList()),
      )
    }
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/uv_workspace_dependency_groups_in_root")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class UvWorkspaceDependencyGroupsInRootOwningTest(project: Project) : UvWorkspaceDependencyGroupsInRootTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/uv_workspace_dependency_groups_in_root")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class UvWorkspaceDependencyGroupsInRootKeepingTest(project: Project) : UvWorkspaceDependencyGroupsInRootTest(project)
