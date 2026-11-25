// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.openapi.project.Project
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.div
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.PYTHON
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.python.pyproject.model.internal.workspaceBridge.PY_PROJECT_MODEL_OWNS_MODULES_KEY
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import org.junit.jupiter.api.Test

internal abstract class MonorepoSourceRootCheckTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  /**
   * PY-88898: `poetryone` uses the standard `src`, while `poetrytwo` and `poetrythree` name their own directory
   * in `[tool.poetry] packages`. `uvone` has a flat layout and therefore no source root.
   */
  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("poetryone", contentRoot = "poetryone", sourceRoots = listOf("poetryone" / "src")),
      ExpectedModule("poetrytwo", contentRoot = "poetrytwo", sourceRoots = listOf("poetrytwo" / "srctwo")),
      ExpectedModule("poetrythree", contentRoot = "poetrythree", sourceRoots = listOf("poetrythree" / "srcthree")),
      ExpectedModule("uvone", contentRoot = "uvone"),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/monorepo_source_root_check")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class MonorepoSourceRootCheckOwningTest(project: Project) : MonorepoSourceRootCheckTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/monorepo_source_root_check")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class MonorepoSourceRootCheckKeepingTest(project: Project) : MonorepoSourceRootCheckTest(project)
