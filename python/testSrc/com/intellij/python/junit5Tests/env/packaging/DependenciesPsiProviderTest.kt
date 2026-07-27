// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.packaging

import com.intellij.codeInsight.hints.declarative.impl.inlineInlayTexts
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfoData
import com.intellij.python.junit5Tests.framework.metaInfo.TestMethodInfoData
import com.intellij.python.venv.createVenv
import com.intellij.python.venv.createVenvAdditionalData
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.PyBundle
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.inspections.dependencies.DependenciesInspection
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.RequirementsProviderType
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.packaging.management.TestPythonPackageManager
import com.jetbrains.python.packaging.management.TestPythonPackageManagerService
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.poetry.PyPoetrySdkAdditionalData
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.time.Duration.Companion.minutes
import java.nio.file.Path as NioPath

/**
 * Inspection tests for [com.jetbrains.python.inspections.dependencies.DependenciesPsiProvider] (migrated from JUnit3).
 *
 * Each test method name matches a subdirectory under `testData/packaging/dependenciesPsi/`.
 * The directory's contents are copied into the project root before the test runs; the test then
 * configures the relevant file (`requirements.txt` or `pyproject.toml`) and asserts highlighting
 * via [CodeInsightTestFixture.checkHighlighting].
 */
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath($$"$CONTENT_ROOT/../testData/packaging/dependenciesPsi")
@PyEnvTestCase
@Timeout(60)
internal class DependenciesPsiProviderTest {
  private val tempPathFixture = tempPathFixture()
  private val projectFixture = projectFixture(tempPathFixture, openAfterCreation = true)
  private val moduleFixture = projectFixture.moduleFixture(tempPathFixture, addPathToSourceRoot = true)

  private val sdkFixture = pySdkFixture()

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
  fun unsatisfiedRequirement() = runTest(RequirementsProviderType.REQUIREMENTS_TXT) {
    fixture.checkHighlighting(true, false, true, false)
    assertTrue(fixture.availableIntentions.any { it.text == "Install package mypy" })
  }

  @Test
  fun requirementsInlineHints() = runTest(
    requirementsProviderType = RequirementsProviderType.REQUIREMENTS_TXT,
    packageManagerProvider =
      TestPackageManagerProvider()
        .withPackageInstalled(
          PythonPackage("django", "1.3.1", false),
          PythonPackage("flask", "1.0", false),
          PythonPackage("requests", "1.22.0", false)
        )
  ) {
    fixture.checkHighlighting(true, false, true, false)
    assertEquals(
      listOf("1.3.1", "1.0", "1.22.0").map { PyBundle.message("INLAY.requirements.installed.version", it) },
      fixture.editor.inlineInlayTexts()
    )
  }

  @Test
  fun pyProjectTomlUnsatisfiedRequirement() = runTest(RequirementsProviderType.PYPROJECT_TOML) {
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
    val warning = warnings.single { it.text == "\"mypy\"" }
    assertEquals("Package mypy is not installed", warning.description)
  }

  @Test
  fun pyProjectTomlExtrasNotFlagged() = runTest(
    requirementsProviderType = RequirementsProviderType.PYPROJECT_TOML,
    beforePackageManager = {
      TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(
        project, listOf(PythonPackage("uvicorn", "0.35.0", false))
      )
    }
  ) {
    val warnings = fixture.doHighlighting(HighlightSeverity.WARNING)
    assertTrue(
      warnings.none { it.description?.contains("uvicorn") == true },
      "uvicorn[standard]>=0.35.0 should not be flagged when uvicorn is installed",
    )
  }

  @Test
  fun emptyRequirementsFile() = runTest(RequirementsProviderType.REQUIREMENTS_TXT) {
    fixture.checkHighlighting(true, false, true, false)
    assertTrue(fixture.availableIntentions.any { it.text == "Export packages" })
  }

  @Test
  fun pyProjectTomlOutdated() = runTest(
    requirementsProviderType = RequirementsProviderType.PYPROJECT_TOML,
    packageManagerProvider =
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
  ) {
    fixture.checkHighlighting(true, false, true, false)
  }

  @Test
  fun pyProjectTomlInlineHints() = runTest(
    requirementsProviderType = RequirementsProviderType.PYPROJECT_TOML,
    packageManagerProvider =
      TestPackageManagerProvider()
        .withPackageInstalled(
          PythonPackage("django", "1.3.1", false),
          PythonPackage("flask", "1.0", false),
          PythonPackage("requests", "1.22.0", false)
        )
  ) {
    fixture.checkHighlighting(true, false, true, false)

    assertEquals(
      listOf("1.3.1", "1.0", "1.22.0").map { PyBundle.message("INLAY.requirements.installed.version", it) },
      fixture.editor.inlineInlayTexts()
    )
  }

  @Test
  fun condaDependencies() = genericProviderTest(RequirementsProviderType.ENVIRONMENT_YML)

  @Test
  fun pipenvDependencies() = genericProviderTest(RequirementsProviderType.PIPFILE)

  @Test
  fun legacyPoetryDependencies() =
    genericProviderTest(
      requirementsProviderType = RequirementsProviderType.PYPROJECT_TOML,
      additionalData = { module -> PyPoetrySdkAdditionalData(createVenvAdditionalData(module).getOrThrow().workingDirectory) }
    )

  private fun runTest(
    requirementsProviderType: RequirementsProviderType,
    packageManagerProvider: TestPackageManagerProvider = TestPackageManagerProvider(),
    additionalData: (Module) -> PythonSdkAdditionalData = { createVenvAdditionalData(it).getOrThrow() },
    beforePackageManager: () -> Unit = {},
    body: suspend () -> Unit,
  ) {
    timeoutRunBlocking(5.minutes) {
      ExtensionTestUtil.maskExtensions(
        PythonPackageManagerProvider.EP_NAME,
        listOf(packageManagerProvider),
        fixture.testRootDisposable,
      )

      val env = sdkFixture.get().env
      val sdk = withContext(Dispatchers.EDT) {
        val module = moduleFixture.get()
        val venvDir = tempPathFixture.get().resolve(".venv")
        val venvPython = createVenv(env.pythonPath, venvDir).getOrThrow()
        createSdk(PathHolder.Eel(venvPython), additionalData(module))
          .orThrow()
          .also {
            module.pythonSdk = it
            it.setAssociationToModule(module)
            beforePackageManager()
          }
      }

      sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, requirementsProviderType)
      PythonPackageManager.forSdk(project, sdk).waitForInit()

      fixture.configureFromTempProjectFile(requirementsProviderType.filename)

      try {
        withContext(Dispatchers.EDT) {
          body()
        }
      }
      finally {
        edtWriteAction {
          ProjectJdkTable.getInstance().removeJdk(sdk)
        }
      }
    }
  }

  private fun genericProviderTest(
    requirementsProviderType: RequirementsProviderType,
    additionalData: (Module) -> PythonSdkAdditionalData = { createVenvAdditionalData(it).getOrThrow() },
  ) =
    runTest(
      requirementsProviderType = requirementsProviderType,
      packageManagerProvider =
        TestPackageManagerProvider()
          .withPackageInstalled(
            PythonPackage("python", "3.9", false),
            PythonPackage("django", "1.3.1", false),
            PythonPackage("flask", "1.0", false),
            PythonPackage("requests", "1.22.0", false)
          )
          .withOutdatedPackages(
            PythonOutdatedPackage("django", "1.3.1", "5.0.0"),
            PythonOutdatedPackage("flask", "1.0", "3.0.0"),
          ),
      additionalData = additionalData,
    ) {
      fixture.checkHighlighting(true, false, true, false)

      for (pkg in listOf("django", "flask")) {
        val offset = fixture.editor.document.text.indexOf(pkg)
        fixture.editor.caretModel.moveToOffset(offset)

        val availableIntentions = fixture.availableIntentions.map { it.text }
        val updateFixText = PyBundle.message("QFIX.NAME.update.requirement", pkg)

        assertContainsElements(availableIntentions, updateFixText)
        assertContainsElements(availableIntentions, PyBundle.message("QFIX.NAME.update.all.requirements"))
      }

      for (pkg in listOf("numpy", "pandas")) {
        val offset = fixture.editor.document.text.indexOf(pkg)
        fixture.editor.caretModel.moveToOffset(offset)

        val availableIntentions = fixture.availableIntentions.map { it.text }
        val installFixText = PyBundle.message("QFIX.NAME.install.requirement", pkg)

        assert(availableIntentions.any { it.startsWith(installFixText) })
        assertContainsElements(availableIntentions, PyBundle.message("QFIX.NAME.install.all.requirements"))
      }

      assertEquals(
        listOf("1.3.1", "1.0", "1.22.0").map { PyBundle.message("INLAY.requirements.installed.version", it) },
        fixture.editor.inlineInlayTexts()
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
