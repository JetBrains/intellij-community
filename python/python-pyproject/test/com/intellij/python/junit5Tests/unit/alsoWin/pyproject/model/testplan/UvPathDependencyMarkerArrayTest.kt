// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.idea.TestFor
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.div
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.PYTHON
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * PY-91195, `path =` form: `[tool.uv.sources]` may hold an **array of tables** per dependency (several
 * sources selected by platform marker — valid uv syntax), and the resulting module dependency must be
 * resolved just as it is for the single inline table `subuv1 = { path = "../subuv1" }`.
 *
 * The test data is a copy of [UvPathDependenciesTest]'s `uv_path_dependencies` with that one line turned
 * into the array form, so the expected structure below is the same structure that test already proves —
 * the array form is the only variable.
 *
 * **Why there is no `[tool.uv.workspace]` in the fixture.** `UvPyProjectManager#getProjectStructure`
 * skips any project that is not a workspace member (`memberToWorkspace[name] ?: continue`), so for a
 * plain uv project its array-aware `getUvDependencies` never runs, and the `subuv2 -> subuv1` edge can
 * only come from the generic reader `getToolSpecificDependenciesFromTomlTable`. Inside a workspace the
 * two contributors are unioned by `ProjectDependencies.plus`, and the already-fixed uv resolver would
 * supply the edge on its own — a workspace-scoped version of this test passes today and proves nothing.
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-91195-uv-path-dependency-marker-array")
internal class UvPathDependencyMarkerArrayTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val f by pyProjectTomlSyncFixture(projectFixture)

  @Test
  @TestFor(issues = ["PY-91195"])
  @Disabled(
    "PY-91195: only the `workspace = true` half of the reported root cause is implemented. " +
    "getToolSpecificDependenciesFromTomlTable still reads `<dep>.path` as a single string, so an " +
    "array-of-tables value is dropped and subuv2 gets no dependency on subuv1. Enable this test with " +
    "the fix; it needs no changes of its own."
  )
  fun `path source written as an array of tables still resolves the module dependency`(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("subuv1", contentRoot = "subuv1", sourceRoots = listOf("subuv1" / "src")),
      ExpectedModule("subuv2", contentRoot = "subuv2", deps = listOf("subuv1")),
    )
  }
}
