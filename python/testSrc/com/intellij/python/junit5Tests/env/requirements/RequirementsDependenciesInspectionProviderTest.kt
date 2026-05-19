// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.requirements

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfoData
import com.intellij.python.junit5Tests.framework.metaInfo.TestMethodInfoData
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.test.env.junit5.pyVenvFixture
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
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.inspections.dependencies.DependenciesInspection
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.packaging.management.TestPythonPackageManagerService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Path as NioPath
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively

/**
 * Inspection tests for [com.jetbrains.python.requirements.inspections.tools.RequirementsDependenciesInspectionProvider] (migrated from JUnit3).
 *
 * Each test method name matches a subdirectory under `testData/requirements/inspections/`.
 * The directory's contents are copied into the project root before the test runs; the test then
 * configures the relevant file (`requirements.txt` or `pyproject.toml`) and asserts highlighting
 * via [CodeInsightTestFixture.checkHighlighting].
 */
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath($$"$CONTENT_ROOT/../testData/requirements/inspections")
@PyEnvTestCase
@Timeout(60)
internal class RequirementsDependenciesInspectionProviderTest {
  private val tempPathFixture = tempPathFixture()
  private val projectFixture = projectFixture(tempPathFixture, openAfterCreation = true)
  private val moduleFixture = projectFixture.moduleFixture(tempPathFixture, addPathToSourceRoot = true)

  @Suppress("unused")
  private val venvFixture = pySdkFixture().pyVenvFixture(
    where = tempPathFixture,
    addToSdkTable = true,
    moduleFixture = moduleFixture,
  )

  private val codeInsightFixture = pyCodeInsightFixture(projectFixture, tempPathFixture)

  private val project by projectFixture
  private val fixture by codeInsightFixture

  private lateinit var testDataDir: NioPath

  @OptIn(ExperimentalPathApi::class)
  @BeforeEach
  fun setUp(methodInfo: TestMethodInfoData, classInfo: TestClassInfoData) {
    testDataDir = classInfo.testDataPath!!.resolve(methodInfo.testCaseRelativePath!!)
    val targetPath = tempPathFixture.get()
    testDataDir.copyToRecursively(targetPath, followLinks = false, overwrite = true)
    LocalFileSystem.getInstance().refresh(false)
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    InspectionProfileImpl.INIT_INSPECTIONS = true
    fixture.enableInspections(DependenciesInspection::class.java)
  }

  @AfterEach
  fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
  }

  @Test
  fun unsatisfiedRequirement() {
    setupPackageManager()
    fixture.configureFromTempProjectFile("requirements.txt")
    fixture.checkHighlighting(true, false, true, false)
    assertTrue(fixture.availableIntentions.any { it.text == "Install package mypy" })
  }

  @Test
  fun pyProjectTomlUnsatisfiedRequirement() {
    setupPackageManager()
    fixture.configureFromTempProjectFile(PY_PROJECT_TOML)
    fixture.checkHighlighting(true, false, true, false)
    val warnings = fixture.doHighlighting(HighlightSeverity.WARNING)
    listOf(
      "[build-system].requires" to "poetry-core",
      "[project.optional-dependencies] table" to "sphinx",
      "inline [project].optional-dependencies" to "pytest",
      "[dependency-groups]" to "ruff",
    ).forEach { (sectionDescr, pkg) ->
      assertTrue(
        warnings.none { it.text == pkg },
        "$sectionDescr should not produce a 'package not installed' warning",
      )
    }
    val warning = warnings.single { it.text == "mypy" }
    assertEquals("Package mypy is not installed", warning.description)
  }

  @Test
  fun pyProjectTomlExtrasNotFlagged() {
    setupPackageManager()
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(
      project, listOf(PythonPackage("uvicorn", "0.35.0", false))
    )
    fixture.configureFromTempProjectFile(PY_PROJECT_TOML)
    val warnings = fixture.doHighlighting(HighlightSeverity.WARNING)
    assertTrue(
      warnings.none { it.description?.contains("uvicorn") == true },
      "uvicorn[standard]>=0.35.0 should not be flagged when uvicorn is installed",
    )
  }

  @Test
  fun emptyRequirementsFile() {
    setupPackageManager()
    fixture.configureFromTempProjectFile("requirements.txt")
    fixture.checkHighlighting(true, false, true, false)
    assertTrue(fixture.availableIntentions.any { it.text == "Add imported packages to requirements…" })
  }

  @Test
  fun pyProjectTomlOutdated() {
    setupPackageManager(
      TestPackageManagerProvider()
        .withPackageInstalled(
          PythonPackage("django", "1.3.1", false),
          PythonPackage("flask", "1.0", false),
          PythonPackage("requests", "1.22.0", false)
        )
        .withOutdatedPackages(
          PythonOutdatedPackage("django", "1.3.1", "5.0.0"),
          PythonOutdatedPackage("flask", "1.0", "3.0.0"),
        )
    )
    fixture.configureFromTempProjectFile(PY_PROJECT_TOML)
    fixture.checkHighlighting(true, false, true, false)
  }

  private fun setupPackageManager(provider: TestPackageManagerProvider = TestPackageManagerProvider()) {
    ExtensionTestUtil.maskExtensions(
      PythonPackageManagerProvider.EP_NAME,
      listOf(provider),
      fixture.testRootDisposable,
    )
  }
}

private fun pyCodeInsightFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<NioPath>,
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
    override fun doCreateTempDirectory(): NioPath = tempDir
    override fun deleteOnTearDown(): Boolean = false
  }

  val codeInsightFixture = CodeInsightTestFixtureImpl(ideaProjectFixture, ideaTempDirFixture)
  codeInsightFixture.testDataPath = PythonHelpersLocator.getPythonCommunityPath()
    .resolve("testData/requirements/inspections").toString()
  codeInsightFixture.setUp()
  initialized(codeInsightFixture) {
    codeInsightFixture.tearDown()
  }
}
