// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.junit5.framework.impl.PyTestDataExtension
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

@TestOnly
fun TestFixture<Project>.pyMockSdkFixture(module: TestFixture<Module>, sdkProvider: () -> Sdk):
  TestFixture<Sdk> = testFixture {
  this@pyMockSdkFixture.init()
  module.init()
  val sdk = sdkProvider()
  edtWriteAction {
    ProjectJdkTable.getInstance().addJdk(sdk)
    ModuleRootModificationUtil.setModuleSdk(module.get(), sdk)
  }
  initialized(sdk) {
    edtWriteAction {
      ModuleRootModificationUtil.setModuleSdk(module.get(), null)
      ProjectJdkTable.getInstance().removeJdk(sdk)
    }
  }
}

/**
 * Creates a [CodeInsightTestFixture] without the platform's `@TestDataPath` resolution.
 *
 * The platform's [com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture] resolves
 * `@TestDataPath` using `$PROJECT_ROOT` prefix, which is incompatible with Python tests that use `$CONTENT_ROOT`.
 * The test data path is set later by [PyTestDataExtension].
 */
@TestOnly
fun pyCodeInsightFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<Path>,
): TestFixture<CodeInsightTestFixture> = testFixture {
  val project = projectFixture.init()
  val tempDir = tempDirFixture.init()

  val ideaProjectTestFixture = object : IdeaProjectTestFixture {
    override fun getProject(): Project = project

    override fun getModule(): Module {
      check(project.modules.isNotEmpty()) {
        "At least one module is required for the project. Use TestFixture<Project>.moduleFixture() to register one in your test class."
      }
      return project.modules[0]
    }

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