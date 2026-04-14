// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pyproject.model.testplan

import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.pyproject.model.PYTHON
import com.intellij.python.junit5Tests.unit.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

private const val BASE = $$"$CONTENT_ROOT/../testData/monorepo/some_projects_with_src_nonstandard_naming"

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/flat_layout_poetry")
internal class FlatLayoutPoetryTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("poetry", contentRoot = "poetry"),
      ExpectedModule("uv", contentRoot = "uv", deps = listOf("poetry")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/hatch.build.targets.wheel")
internal class HatchBuildTargetsWheelTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  @Disabled("Hatch custom source root detection is not yet supported")
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("uv1", contentRoot = "uv1", sourceRoots = listOf("uv1/my_src")),
      ExpectedModule("uv2", contentRoot = "uv2", deps = listOf("uv1")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/poetry.packages.include")
internal class PoetryPackagesIncludeTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("poetry1", contentRoot = "poetry1"),
      ExpectedModule("uv2", contentRoot = "uv2", deps = listOf("poetry1")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/setuptools.packages.find")
internal class SetuptoolsPackagesFindTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  @Disabled("Setuptools custom source root detection is not yet supported")
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("uv1", contentRoot = "uv1", sourceRoots = listOf("uv1/my_src")),
      ExpectedModule("uv2", contentRoot = "uv2", deps = listOf("uv1")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/setuptools.packages.find_multiple_source_roots")
internal class SetuptoolsPackagesFindMultipleSourceRootsTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  @Disabled("Setuptools multiple source root detection is not yet supported")
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("uv1", contentRoot = "uv1", sourceRoots = listOf("uv1/my_src", "uv1/my_src2")),
      ExpectedModule("uv2", contentRoot = "uv2", deps = listOf("uv1")),
    )
  }
}

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath("$BASE/uv.build-backend")
internal class UvBuildBackendTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  @Disabled("uv build-backend custom source root detection is not yet supported")
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("uv1", contentRoot = "uv1", sourceRoots = listOf("uv1/my_src")),
      ExpectedModule("uv2", contentRoot = "uv2", deps = listOf("uv1")),
    )
  }
}
