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

internal abstract class MonorepoUvWorkspaceSetOnParentTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("8840337-uv-workspace", contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("myorg-core", contentRoot = "libs" / "core", sourceRoots = listOf("libs" / "core" / "src")),
      ExpectedModule("myorg-frontend",
                     contentRoot = "apps" / "frontend",
                     sourceRoots = listOf("apps" / "frontend" / "src"),
                     deps = listOf("myorg-core")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/8840337-uv-workspace")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class MonorepoUvWorkspaceSetOnParentOwningTest(project: Project) : MonorepoUvWorkspaceSetOnParentTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/8840337-uv-workspace")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class MonorepoUvWorkspaceSetOnParentKeepingTest(project: Project) : MonorepoUvWorkspaceSetOnParentTest(project)
