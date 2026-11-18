// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework

import com.intellij.python.sdk.backend.addMockPythonInterpreter
import com.jetbrains.python.project.PyProject
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.python.pyproject.model.internal.platformBridge.rebuildPyProjectModelForTest
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.junit5.framework.impl.PyTestDataExtension
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

@TestOnly
fun TestFixture<Project>.pyMockInterpreterFixture(pyProject: TestFixture<PyProject>, sdkProvider: () -> Sdk):
  TestFixture<PythonInterpreter> = testFixture {
  val project = this@pyMockInterpreterFixture.init()
  val pyProject = pyProject.init()
  val registry = PythonInterpreterRegistry.getInstance(project)
  val interpreter = registry.addMockPythonInterpreter(pyProject, sdkProvider())
  // setPythonInterpreter waits for the snapshot, so a test that highlights next is not cancelled by its restart.
  pyProject.setPythonInterpreter(interpreter)
  initialized(interpreter) {
    pyProject.setPythonInterpreter(null)
    registry.remove(interpreter)
  }
}

/**
 * Creates a [CodeInsightTestFixture] without the platform's `@TestDataPath` resolution.
 *
 * The platform's `com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture` resolves
 * `@TestDataPath` using `$PROJECT_ROOT` prefix, which is incompatible with Python tests that use `$CONTENT_ROOT`.
 * The test data path is set later by [PyTestDataExtension].
 */
@TestOnly
fun pyCodeInsightFixture(
  projectFixture: TestFixture<Project>,
  moduleFixture: TestFixture<Module>,
  tempDirFixture: TestFixture<Path>,
): TestFixture<CodeInsightTestFixture> = testFixture {
  val project = projectFixture.init()
  val module = moduleFixture.init()
  val tempDir = tempDirFixture.init()

  val ideaProjectTestFixture = object : IdeaProjectTestFixture {
    override fun getProject(): Project = project

    override fun getModule(): Module = module

    override fun setUp() {
      TestApplicationManager.getInstance().setDataProvider(TestDataProvider(project))
    }

    override fun tearDown() {
      TestApplicationManager.getInstance().setDataProvider(null)
    }
  }

  val tempDirTestFixture = object : TempDirTestFixtureImpl() {
    override fun doCreateTempDirectory(): Path = tempDir
    override fun deleteOnTearDown(): Boolean = false
  }

  val codeInsightFixture = CodeInsightTestFixtureImpl(ideaProjectTestFixture, tempDirTestFixture)
  // Ensure the temp directory is registered in VFS before setUp().
  // Implicit fixtures are initialized concurrently (see registerImplicitFixtures),
  // so the sourceRootFixture (which calls VfsUtil.createDirectories) may not have run yet.
  // Without this, CodeInsightTestFixtureImpl.setUp() fails at getFile("") because VFS
  // doesn't know about the temp directory path.
  VfsUtil.createDirectories(tempDir.toString())
  codeInsightFixture.setUp()
  initialized(codeInsightFixture) {
    codeInsightFixture.tearDown()
  }
}

/** Creates and reloads a blueprint-backed Python external-system project (uv, Poetry, and similar). */
@ApiStatus.Experimental
@TestOnly
fun pyExternalSystemProjectFixture(
  blueprintResourcePath: Path,
  pathFixture: TestFixture<Path> = tempPathFixture(),
): TestFixture<Project> {
  val projectWithBlueprint = projectFixture(
    pathFixture = pathFixture,
    openAfterCreation = true,
    blueprintResourcePath = blueprintResourcePath,
  )
  return testFixture {
    val project = projectWithBlueprint.init()
    rebuildPyProjectModelForTest(project)
    initialized(project) {}
  }
}
