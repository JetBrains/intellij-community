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
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-90207-uv-workspace-normalized-root-sources")
internal class UvWorkspaceNormalizedRootSourcesTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  /**
   * PY-90207: the workspace root declares its `[tool.uv.sources]` keys in normalized (PEP 503) form —
   * `example-failure` — while the member `example.parent` lists the dependency under its distribution name
   * `example.failure`. `example-failure` and `example.failure` are equivalent names, so the workspace
   * dependency must resolve. The mixed `"example.success"` key (dotted, already matching) guards the regression.
   * The `EXAMPLE_EXTRA` key / `Example.Extra` dependency additionally cover case- and underscore-insensitivity:
   * all three of `EXAMPLE_EXTRA`, `Example.Extra` and the member name `example.extra` normalize to `example-extra`.
   */
  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("pycharm-uv-workspace-repro", contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("example.failure", contentRoot = "packages" / "example" / "failure", sourceRoots = listOf("packages" / "example" / "failure" / "src")),
      ExpectedModule("example.parent", contentRoot = "packages" / "example" / "parent", deps = listOf("example.failure", "example.success", "example.extra"), sourceRoots = listOf("packages" / "example" / "parent" / "src")),
      ExpectedModule("example.success", contentRoot = "packages" / "example" / "success", sourceRoots = listOf("packages" / "example" / "success" / "src")),
      ExpectedModule("example.extra", contentRoot = "packages" / "example" / "extra", sourceRoots = listOf("packages" / "example" / "extra" / "src")),
    )
  }
}
