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

internal abstract class MonorepoWithSeparatePyprojectForPytestTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("monorepo-with-a-separate-pyproject-for-pytest", contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("uvlib1", contentRoot = "lib" / "uvlib1"),
      ExpectedModule("uvlib2", contentRoot = "lib" / "uvlib2"),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/monorepo_with_a_separate_pyproject_for_pytest")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class MonorepoWithSeparatePyprojectForPytestOwningTest(project: Project) : MonorepoWithSeparatePyprojectForPytestTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/monorepo_with_a_separate_pyproject_for_pytest")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class MonorepoWithSeparatePyprojectForPytestKeepingTest(project: Project) : MonorepoWithSeparatePyprojectForPytestTest(project)
