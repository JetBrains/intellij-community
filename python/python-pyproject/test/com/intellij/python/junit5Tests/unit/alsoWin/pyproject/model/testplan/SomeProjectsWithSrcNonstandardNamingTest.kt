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

private const val BASE = $$"$CONTENT_ROOT/../testData/monorepo/some_projects_with_src_nonstandard_naming"

/**
 * PY-88898: each sample here holds its packages in a directory that is not named `src`, and declares that
 * directory in `pyproject.toml`. The build backend of the sample names the table that carries the declaration.
 */
internal abstract class FlatLayoutPoetryTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    // A flat layout keeps its packages in the content root, so it declares no source root.
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("poetry", contentRoot = "poetry"),
      ExpectedModule("uv", contentRoot = "uv", deps = listOf("poetry")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/flat_layout_poetry")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class FlatLayoutPoetryOwningTest(project: Project) : FlatLayoutPoetryTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/flat_layout_poetry")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class FlatLayoutPoetryKeepingTest(project: Project) : FlatLayoutPoetryTest(project)

internal abstract class HatchBuildTargetsWheelTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("uv1", contentRoot = "uv1", sourceRoots = listOf("uv1" / "my_src")),
      ExpectedModule("uv2", contentRoot = "uv2", deps = listOf("uv1")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/hatch.build.targets.wheel")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class HatchBuildTargetsWheelOwningTest(project: Project) : HatchBuildTargetsWheelTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/hatch.build.targets.wheel")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class HatchBuildTargetsWheelKeepingTest(project: Project) : HatchBuildTargetsWheelTest(project)

internal abstract class PoetryPackagesIncludeTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("poetry1", contentRoot = "poetry1", sourceRoots = listOf("poetry1" / "my_src")),
      ExpectedModule("uv2", contentRoot = "uv2", deps = listOf("poetry1")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/poetry.packages.include")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class PoetryPackagesIncludeOwningTest(project: Project) : PoetryPackagesIncludeTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/poetry.packages.include")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class PoetryPackagesIncludeKeepingTest(project: Project) : PoetryPackagesIncludeTest(project)

internal abstract class SetuptoolsPackagesFindTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("uv1", contentRoot = "uv1", sourceRoots = listOf("uv1" / "my_src")),
      ExpectedModule("uv2", contentRoot = "uv2", deps = listOf("uv1")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/setuptools.packages.find")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class SetuptoolsPackagesFindOwningTest(project: Project) : SetuptoolsPackagesFindTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/setuptools.packages.find")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class SetuptoolsPackagesFindKeepingTest(project: Project) : SetuptoolsPackagesFindTest(project)

internal abstract class SetuptoolsPackagesFindMultipleSourceRootsTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("uv1", contentRoot = "uv1", sourceRoots = listOf("uv1" / "my_src", "uv1" / "my_src2")),
      ExpectedModule("uv2", contentRoot = "uv2", deps = listOf("uv1")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/setuptools.packages.find_multiple_source_roots")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class SetuptoolsPackagesFindMultipleSourceRootsOwningTest(project: Project) : SetuptoolsPackagesFindMultipleSourceRootsTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/setuptools.packages.find_multiple_source_roots")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class SetuptoolsPackagesFindMultipleSourceRootsKeepingTest(project: Project) : SetuptoolsPackagesFindMultipleSourceRootsTest(project)

internal abstract class UvBuildBackendTest(project: Project) {
  private val f by pyProjectTomlSyncFixture(project)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("uv1", contentRoot = "uv1", sourceRoots = listOf("uv1" / "my_src")),
      ExpectedModule("uv2", contentRoot = "uv2", deps = listOf("uv1")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/uv.build-backend")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "true")
internal class UvBuildBackendOwningTest(project: Project) : UvBuildBackendTest(project)

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/uv.build-backend")
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class UvBuildBackendKeepingTest(project: Project) : UvBuildBackendTest(project)
