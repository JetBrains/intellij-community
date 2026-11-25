// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.idea.TestFor
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

/**
 * PY-91195 follow-up: each `[tool.uv.sources]` entry is an array of tables (multiple sources selected
 * by platform marker — a valid uv feature), and the workspace is declared with the inline-table form
 * `[tool.uv] workspace = { members = [...] }`. All members must be discovered as modules.
 */
internal abstract class UvSourcesMarkerArrayTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  @Test
  @TestFor(issues = ["PY-91195"])
  fun `workspace sources written as arrays of tables resolve both members and their dependency edges`(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("pythonproject", contentRoot = ".", deps = listOf("sub-project-a", "sub-project-b"), sourceRoots = listOf(".")),
      ExpectedModule("sub-project-a",
                     contentRoot = "sub-projects" / "sub-project-a",
                     sourceRoots = listOf("sub-projects" / "sub-project-a" / "src")),
      ExpectedModule("sub-project-b",
                     contentRoot = "sub-projects" / "sub-project-b",
                     sourceRoots = listOf("sub-projects" / "sub-project-b" / "src")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-91195-uv-sources-marker-array")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class UvSourcesMarkerArrayOwningTest(project: Project) : UvSourcesMarkerArrayTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-91195-uv-sources-marker-array")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class UvSourcesMarkerArrayKeepingTest(project: Project) : UvSourcesMarkerArrayTest(project)
