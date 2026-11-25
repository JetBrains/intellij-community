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

internal abstract class UvWorkspace2CopiesWithDependenciesTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  /**
   * Two identical UV workspace copies live side-by-side, so every module name appears twice.
   * The dedup logic assigns `@1` suffixes to the second copy.
   * Both roots and both `package_b` modules depend on their respective `package_a`
   * via `workspace = true`.
   *
   * The key assertion: deduped modules (`my-uv-monorepo@1`, `package_b@1`) must resolve
   * their workspace dependency to `package_a@1`
   * (the deduped name), not drop it because the natural name "package_a" doesn't match.
   */
  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("my-uv-monorepo", contentRoot = "copy1", deps = listOf("package_a")),
      ExpectedModule("my-uv-monorepo@1", contentRoot = "copy2", deps = listOf("package_a@1")),
      ExpectedModule("package_a", contentRoot = "copy1" / "packages" / "package_a"),
      ExpectedModule("package_a@1", contentRoot = "copy2" / "packages" / "package_a"),
      ExpectedModule("package_b", contentRoot = "copy1" / "packages" / "package_b", deps = listOf("package_a")),
      ExpectedModule("package_b@1", contentRoot = "copy2" / "packages" / "package_b", deps = listOf("package_a@1")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/uv_workspace_2copies_with_dependencies")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class UvWorkspace2CopiesWithDependenciesOwningTest(project: Project) : UvWorkspace2CopiesWithDependenciesTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/uv_workspace_2copies_with_dependencies")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class UvWorkspace2CopiesWithDependenciesKeepingTest(project: Project) : UvWorkspace2CopiesWithDependenciesTest(project)
