// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.packaging

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfoData
import com.intellij.python.junit5Tests.framework.metaInfo.TestMethodInfoData
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.packaging.PyPackageRequirementsSettings
import com.jetbrains.python.packaging.PyRequirementsVersionSpecifierType
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.TestPythonPackageManagerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Path as NioPath
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.CopyActionResult
import kotlin.io.path.copyToRecursively
import kotlin.io.path.readText

/**
 * Tests for requirements.txt generation via the `PySyncPythonRequirements` action (Migrated from JUnit3).
 *
 * Each test method name matches a subdirectory under `testData/requirement/generation/`.
 * Input files (`main.py`, `requirements.txt`, `base_requirements.txt`) are copied to the module root;
 * after the action runs, the result is compared against `new_requirements.txt` (and `new_base_requirements.txt`).
 */
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath($$"$CONTENT_ROOT/../testData/requirement/generation")
@PyEnvTestCase
@Timeout(60)
internal class PyRequirementsTxtGenerationTest {
  private val tempPathFixture = tempPathFixture()
  private val projectFixture = projectFixture()
  private val moduleFixture = projectFixture.moduleFixture(tempPathFixture)
  private val sourceRoot = moduleFixture.sourceRootFixture(
    pathFixture = projectFixture.pathInProjectFixture(Path("")),
  )

  @Suppress("unused")
  private val venvFixture = pySdkFixture().pyVenvFixture(
    where = tempPathFixture,
    addToSdkTable = true,
    moduleFixture = moduleFixture,
  )

  private val project get() = projectFixture.get()
  private val module get() = moduleFixture.get()

  private lateinit var testDataDir: NioPath

  @OptIn(ExperimentalPathApi::class)
  @BeforeEach
  fun setUp(methodInfo: TestMethodInfoData, classInfo: TestClassInfoData) {
    testDataDir = classInfo.testDataPath!!.resolve(methodInfo.testCaseRelativePath!!)
    val targetPath = sourceRoot.get().virtualFile.toNioPath()
    testDataDir.copyToRecursively(targetPath, followLinks = false) { source, target ->
      if (source.fileName.startsWith("new_")) CopyActionResult.SKIP_SUBTREE
      else source.copyToIgnoringExistingDirectory(target, followLinks = false)
    }
    LocalFileSystem.getInstance().refresh(false)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  @Test
  fun newFileGeneration() = doTest()
  @Test
  fun newFileWithoutVersion() = doTest(versionSpecifier = PyRequirementsVersionSpecifierType.NO_VERSION)
  @Test
  fun addMissingPackages() = doTest()
  @Test
  fun addMissingVersion() = doTest()
  @Test
  fun stripVersion() = doTest(versionSpecifier = PyRequirementsVersionSpecifierType.NO_VERSION)
  @Test
  fun addVersionWithSpecifier() = doTest(versionSpecifier = PyRequirementsVersionSpecifierType.COMPATIBLE)
  @Test
  fun keepComments() = doTest(removeUnused = true)
  @Test
  fun keepMatchingVersion() = doTest()
  @Test
  fun keepFileInstallOptions() = doTest()
  @Test
  fun keepPackageInstallOptions() = doTest()
  @Test
  fun keepEditableFromVCS() = doTest()
  @Test
  fun keepEditableForSelf() = doTest()
  @Test
  fun removeUnused() = doTest(removeUnused = true)
  @Test
  fun updateVersionKeepInstallOptions() = doTest()
  @Test
  fun compatibleFileReference() = doTest()
  @Test
  fun differentTopLevelImport() = doTest()
  @Test
  fun differentTopLevelImportWithOriginalPackage() = doTest(
    packages = DEFAULT_PACKAGES + PythonPackage("docker", "3.7.0", false),
  )
  @Test
  fun baseFileUnchanged() = doTest()
  @Test
  fun baseFileUpdate() = doTest(modifyBaseFiles = true)
  @Test
  fun baseFileCleanup() = doTest(modifyBaseFiles = true, removeUnused = true)

  private fun doTest(
    versionSpecifier: PyRequirementsVersionSpecifierType = PyRequirementsVersionSpecifierType.STRONG_EQ,
    removeUnused: Boolean = false,
    modifyBaseFiles: Boolean = false,
    packages: List<PythonPackage> = DEFAULT_PACKAGES,
  ) {
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(project, packages)

    val settings = PyPackageRequirementsSettings.getInstance(module)
    settings.versionSpecifier = versionSpecifier
    settings.removeUnused = removeUnused
    settings.modifyBaseFiles = modifyBaseFiles

    runSyncAction()

    val expected = testDataDir.resolve("new_requirements.txt").readText()
    val actual = readModuleFileContent("requirements.txt")
    assertEquals(expected, actual, "requirements.txt content mismatch")

    if (modifyBaseFiles) {
      val expectedBase = testDataDir.resolve("new_base_requirements.txt").readText()
      val actualBase = readModuleFileContent("base_requirements.txt")
      assertEquals(expectedBase, actualBase, "base_requirements.txt content mismatch")
    }
  }

  private fun runSyncAction() {
    ApplicationManager.getApplication().invokeAndWait {
      val action = ActionManager.getInstance().getAction("PySyncPythonRequirements")
      val context = SimpleDataContext.getSimpleContext(PlatformCoreDataKeys.MODULE, module)
      val event = AnActionEvent.createFromAnAction(action, null, "", context)
      action.actionPerformed(event)
    }
  }

  private fun readModuleFileContent(fileName: String): String? {
    return ReadAction.compute<String?, Throwable> {
      val moduleDir = module.guessModuleDir() ?: return@compute null
      val file = moduleDir.findChild(fileName) ?: return@compute null
      FileDocumentManager.getInstance().getDocument(file)?.text
    }
  }

  companion object {
    private val DEFAULT_PACKAGES = listOf(
      PythonPackage("Django", "3.0.0", false),
      PythonPackage("requests", "2.22.0", false),
      PythonPackage("Jinja2", "2.11.1", false),
      PythonPackage("pandas", "1.0.1", false),
      PythonPackage("cookiecutter", "1.7.0", false),
      PythonPackage("numpy", "1.18.1", false),
      PythonPackage("tox", "3.14.4", false),
      PythonPackage("docker-py", "1.10.6", false),
    )
  }
}
