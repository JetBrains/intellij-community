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

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/PY-89677-uv-workspace-dot-dash-resolve")
internal class UvWorkspaceDotDashResolveTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  /**
   * PY-89677: `acme.eval` declares its dependencies (`acme.rag.specs`, `acme.rag`) and the matching
   * `[tool.uv.sources]` `{ workspace = true }` entries using dotted distribution names, while the
   * workspace members publish their normalized names `acme-rag-specs` / `acme-rag`. Per the Python
   * name-normalization rules `acme.rag.specs` and `acme-rag-specs` are the same package, so both
   * workspace dependencies must resolve to the sibling modules regardless of the `.`/`-`/`_` spelling.
   */
  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("acme-workspace", contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("acme.eval", contentRoot = "eval", deps = listOf("acme-rag-specs", "acme-rag")),
      ExpectedModule("acme-rag", contentRoot = "rag"),
      ExpectedModule("acme-rag-specs", contentRoot = "specs"),
    )
  }
}
