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

internal abstract class UvWorkspaceDependentSubprojectInRootTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("pythonproject", contentRoot = ".", deps = listOf("subinroot"), sourceRoots = listOf(".")),
      ExpectedModule("subinroot", contentRoot = "subinroot", sourceRoots = listOf("subinroot" / "src")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/uv_workspace_dependent_subproject_in_root")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class UvWorkspaceDependentSubprojectInRootOwningTest(project: Project) : UvWorkspaceDependentSubprojectInRootTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/uv_workspace_dependent_subproject_in_root")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class UvWorkspaceDependentSubprojectInRootKeepingTest(project: Project) : UvWorkspaceDependentSubprojectInRootTest(project)
