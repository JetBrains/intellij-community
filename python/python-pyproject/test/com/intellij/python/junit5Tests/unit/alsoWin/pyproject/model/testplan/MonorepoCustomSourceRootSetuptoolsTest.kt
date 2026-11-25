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

internal abstract class MonorepoCustomSourceRootSetuptoolsTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  /**
   * PY-88898: both projects declare their own source directory in `[tool.setuptools.packages.find] where`.
   */
  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("myprj1", contentRoot = "prj1", sourceRoots = listOf("prj1" / "mysrc1")),
      ExpectedModule("myprj2", contentRoot = "prj2", sourceRoots = listOf("prj2" / "mysrc2")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/monorepo_custom_source_root_setuptools")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class MonorepoCustomSourceRootSetuptoolsOwningTest(project: Project) : MonorepoCustomSourceRootSetuptoolsTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/monorepo_custom_source_root_setuptools")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class MonorepoCustomSourceRootSetuptoolsKeepingTest(project: Project) : MonorepoCustomSourceRootSetuptoolsTest(project)
