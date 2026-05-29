// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.SEP
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Test

/**
 * A `path = "..."` source declared on the parent workspace must be resolved against the parent's
 * pyproject.toml directory — not against the member's. Without that, the inherited path silently
 * fails to map to a module.
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/8840337-uv-workspace-path-from-parent")
internal class MonorepoUvPathSourceFromParentTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }

  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun pathSourceOnParentIsResolvedAgainstParentRoot(): Unit = timeoutRunBlocking {
    f.reloadProject()
    // monorepo-root picks up external_lib via the generic `tool.uv.sources` path-dep collector
    // (which always resolves relative to the owning pyproject — that's never been buggy).
    // The actual regression check is `frontend` — it can only see external_lib if the inherited
    // path source is resolved against the parent's root, not the child's.
    f.assertProjectStructure(
      ExpectedModule("monorepo-root", contentRoot = ".", sourceRoots = listOf("."), deps = listOf("external_lib")),
      ExpectedModule("external_lib", contentRoot = "external_lib"),
      ExpectedModule("frontend", contentRoot = "apps${SEP}frontend", deps = listOf("external_lib")),
    )
  }
}
