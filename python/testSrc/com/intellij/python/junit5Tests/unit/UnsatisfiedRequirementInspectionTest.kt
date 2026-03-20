// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.test.env.junit5.pyUvVenvFixture
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.requirements.inspections.tools.NotInstalledRequirementInspection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@PyEnvTestCase
@TestDataPath("\$CONTENT_ROOT/../junit5Tests-framework/testResources/UnsatisfiedRequirementInspectionTest")
class UnsatisfiedRequirementInspectionTest {

  private val testDisposable by disposableFixture()
  private val tempDir = tempPathFixture()
  private val project = projectFixture(tempDir, openAfterCreation = true)
  private val module = project.moduleFixture(tempDir, addPathToSourceRoot = true)

  @Suppress("unused")
  private val venvFixture = pySdkFixture().pyUvVenvFixture(
    addToSdkTable = true,
    moduleFixture = module,
  )

  private val fixture = pyCodeInsightFixture(project, tempDir)

  @BeforeEach
  fun setUp() {
    InspectionProfileImpl.INIT_INSPECTIONS = true
    IndexingTestUtil.waitUntilIndexesAreReady(project.get())
    fixture.get().enableInspections(NotInstalledRequirementInspection::class.java)
  }

  @AfterEach
  fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
  }

  @Test
  fun testUnsatisfiedRequirement() {
    val f = fixture.get()
    f.copyDirectoryToProject("UnsatisfiedRequirement", "")
    f.configureFromTempProjectFile("requirements.txt")
    f.checkHighlighting(true, false, true, false)
    assertTrue(f.availableIntentions.any { it.text == "Install package mypy" })
  }

  @Test
  fun testPyProjectTomlExtrasNotFlagged() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("uvicorn", "0.35.0", false))
    ExtensionTestUtil.maskExtensions(PythonPackageManagerProvider.EP_NAME, listOf(provider), testDisposable)

    val f = fixture.get()
    f.copyDirectoryToProject("PyProjectTomlExtrasNotFlagged", "")
    f.configureFromTempProjectFile(PY_PROJECT_TOML)
    f.checkHighlighting(true, false, true, false)
    val warnings = f.doHighlighting(HighlightSeverity.WARNING)
    assertTrue(
      warnings.none { it.description?.contains("uvicorn") == true },
      "uvicorn[standard]>=0.35.0 should not be flagged when uvicorn is installed",
    )
  }

  @Test
  fun testEmptyRequirementsFile() {
    val f = fixture.get()
    f.copyDirectoryToProject("EmptyRequirementsFile", "")
    f.configureFromTempProjectFile("requirements.txt")
    f.checkHighlighting(true, false, true, false)
    assertTrue(f.availableIntentions.any { it.text == "Add imported packages to requirements\u2026" })
  }
}

private fun pyCodeInsightFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<Path>,
): TestFixture<CodeInsightTestFixture> = testFixture {
  val project = projectFixture.init()
  val tempDir = tempDirFixture.init()

  val ideaProjectFixture = object : IdeaProjectTestFixture {
    override fun getProject(): Project = project
    override fun getModule() = project.modules[0]
    override fun setUp() {
      TestApplicationManager.getInstance().setDataProvider(TestDataProvider(project))
    }

    override fun tearDown() {
      TestApplicationManager.getInstance().setDataProvider(null)
    }
  }
  val ideaTempDirFixture = object : TempDirTestFixtureImpl() {
    override fun doCreateTempDirectory(): Path = tempDir
    override fun deleteOnTearDown(): Boolean = false
  }

  val codeInsightFixture = CodeInsightTestFixtureImpl(ideaProjectFixture, ideaTempDirFixture)
  codeInsightFixture.testDataPath = PythonHelpersLocator.getPythonCommunityPath().resolve("junit5Tests-framework/testResources/UnsatisfiedRequirementInspectionTest").toString()
  codeInsightFixture.setUp()
  initialized(codeInsightFixture) {
    codeInsightFixture.tearDown()
  }
}
