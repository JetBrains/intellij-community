// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.LookupFixture
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.getClassLevelLookupFixtureManager
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.getLookupFixtureManager
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.registerImplicitFixtures
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonMockSdk
import com.jetbrains.python.junit5.framework.pyMockSdkFixture
import com.jetbrains.python.psi.LanguageLevel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path

private const val DEFAULT_PROJECT: String = "DEFAULT_PROJECT"
private const val DEFAULT_PY_MODULE: String = "DEFAULT_PY_MODULE"
private const val DEFAULT_TMP_DIR: String = "DEFAULT_TMP_DIR"
private const val MOCK_SDK: String = "MOCK_SDK"
private const val DEFAULT_SOURCE_ROOT: String = "DEFAULT_SOURCE_ROOT"
private const val DEFAULT_CODE_INSIGHT: String = "DEFAULT_CODE_INSIGHT"

internal class PyCodeInsightFixturesExtension : BeforeAllCallback, BeforeEachCallback, Extension {

  override fun beforeAll(context: ExtensionContext) {
    val manager = context.getLookupFixtureManager()

    val implicitFixtures = mutableListOf<LookupFixture>()

    val tmpDirFixture = manager.getOrDefault {
      tempPathFixture().also {
        implicitFixtures += LookupFixture(DEFAULT_TMP_DIR, it, true)
      }
    }

    val project = manager.getOrDefault {
      projectFixture(openAfterCreation = true, pathFixture = tmpDirFixture).also {
        implicitFixtures += LookupFixture(DEFAULT_PROJECT, it, true)
      }
    }

    val module = manager.getOrDefault {
      project.moduleFixture(name = context.uniqueId, moduleType = PyNames.PYTHON_MODULE_ID).also {
        implicitFixtures += LookupFixture(DEFAULT_PY_MODULE, it, true)
      }
    }

    manager.getOrDefault {
      project.pyMockSdkFixture(module) { PythonMockSdk.create(LanguageLevel.getLatest()) }.also {
        implicitFixtures += LookupFixture(MOCK_SDK, it, true)
      }
    }

    runBlocking {
      context.registerImplicitFixtures(implicitFixtures, static = true)
    }

    IndexingTestUtil.waitUntilIndexesAreReady(project.get())
  }

  override fun beforeEach(context: ExtensionContext) {
    val manager = context.getLookupFixtureManager()
    val implicitFixtures = mutableListOf<LookupFixture>()

    val classLevelManager = context.getClassLevelLookupFixtureManager()
    val project = classLevelManager.getRequired<Project>()
    val tempDirFixture = classLevelManager.getRequired<Path>()
    val module = classLevelManager.getRequired<Module>()

    manager.getOrDefault {
      module.sourceRootFixture(
        pathFixture = tempDirFixture,
      ).also {
        implicitFixtures += LookupFixture(DEFAULT_SOURCE_ROOT, it, true)
      }
    }

    manager.getOrDefault {
      pyCodeInsightFixture(project, tempDirFixture).also {
        implicitFixtures += LookupFixture(DEFAULT_CODE_INSIGHT, it, true)
      }
    }

    runBlocking {
      context.registerImplicitFixtures(implicitFixtures, static = false)
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
private fun pyCodeInsightFixture(
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