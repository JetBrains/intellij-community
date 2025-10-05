// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.registerServiceInstance
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.TestPythonPackageManagerService
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.setAssociationToModuleAsync
import org.easymock.EasyMock

class PyRequirementsGenerationTest : PyTestCase() {
  private var oldPackageManagers: PyPackageManagers? = null
  private val installedPackages = mapOf("Django" to "3.0.0",
                                        "requests" to "2.22.0",
                                        "Jinja2" to "2.11.1",
                                        "pandas" to "1.0.1",
                                        "cookiecutter" to "1.7.0",
                                        "numpy" to "1.18.1",
                                        "tox" to "3.14.4",
                                        "docker-py" to "1.10.6")

  fun testNewFileGeneration() = doTest()
  fun testNewFileWithoutVersion() = doTest(PyRequirementsVersionSpecifierType.NO_VERSION)
  fun testAddMissingPackages() = doTest()
  fun testAddMissingVersion() = doTest()
  fun testStripVersion() = doTest(PyRequirementsVersionSpecifierType.NO_VERSION)
  fun testAddVersionWithSpecifier() = doTest(PyRequirementsVersionSpecifierType.COMPATIBLE)
  fun testKeepComments() = doTest(removeUnused = true)
  fun testKeepMatchingVersion() = doTest()
  fun testKeepFileInstallOptions() = doTest()
  fun testKeepPackageInstallOptions() = doTest()
  fun testKeepEditableFromVCS() = doTest()
  fun testKeepEditableForSelf() = doTest()
  fun testRemoveUnused() = doTest(removeUnused = true)
  fun testUpdateVersionKeepInstallOptions() = doTest()
  fun testCompatibleFileReference() = doTest()
  fun testDifferentTopLevelImport() = doTest()
  fun testDifferentTopLevelImportWithOriginalPackage() = doTest(packages = installedPackages + mapOf("docker" to "3.7.0"))
  fun testBaseFileUnchanged() = doTest()
  fun testBaseFileUpdate() = doTest(modifyBaseFiles = true)
  fun testBaseFileCleanup() = doTest(modifyBaseFiles = true, removeUnused = true)

  private fun doTest(
    versionSpecifier: PyRequirementsVersionSpecifierType = PyRequirementsVersionSpecifierType.STRONG_EQ,
    removeUnused: Boolean = false,
    modifyBaseFiles: Boolean = false,
    packages: Map<String, String> = installedPackages,
  ) {
    val module = myFixture.getModule()
    val sdk = PythonSdkUtil.findPythonSdk(module)
    sdk!!.setAssociationToModuleAsync(module)
    val settings = PyPackageRequirementsSettings.getInstance(myFixture.module)

    val oldVersionSpecifier = settings.versionSpecifier
    val oldRemoveUnused = settings.removeUnused
    val oldModifyBaseFiles = settings.modifyBaseFiles

    try {
      overrideInstalledPackages(packages)

      settings.versionSpecifier = versionSpecifier
      settings.removeUnused = removeUnused
      settings.modifyBaseFiles = modifyBaseFiles

      val testName = getTestName(true)
      myFixture.copyDirectoryToProject(testName, "")
      myFixture.configureFromTempProjectFile("requirements.txt")

      val action = ActionManager.getInstance().getAction("PySyncPythonRequirements")
      val context = SimpleDataContext.getSimpleContext(PlatformCoreDataKeys.MODULE, myFixture.module, (myFixture.editor as EditorEx).dataContext)
      val event = AnActionEvent.createFromAnAction(action, null, "", context)
      action.actionPerformed(event)
      myFixture.checkResultByFile("$testName/new_requirements.txt", true)
      if (modifyBaseFiles) {
        myFixture.checkResultByFile("base_requirements.txt", "$testName/new_base_requirements.txt", true)
      }
      assertProjectFilesNotParsed(myFixture.file)
    }
    finally {
      settings.versionSpecifier = oldVersionSpecifier
      settings.removeUnused = oldRemoveUnused
      settings.modifyBaseFiles = oldModifyBaseFiles
    }
  }

  override fun setUp() {
    super.setUp()
    oldPackageManagers = PyPackageManagers.getInstance()
  }

  override fun tearDown() {
    try {
      ApplicationManager.getApplication().registerServiceInstance(PyPackageManagers::class.java, oldPackageManagers!!)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  override fun getTestDataPath(): String = super.getTestDataPath() + "/requirement/generation"

  private fun overrideInstalledPackages(packages: Map<String, String>) {
    ApplicationManager.getApplication().registerServiceInstance(PyPackageManagers::class.java, MockPyPackageManagers(packages))

    val packages = packages.map { PythonPackage(it.key, it.value, false) }
    TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance(project = myFixture.project, packages)
  }

  private class MockPyPackageManagers(val packages: Map<String, String>) : PyPackageManagers() {
    override fun forSdk(sdk: Sdk): PyPackageManager {
      val packageManager = EasyMock.createMock<PyPackageManager>(PyPackageManager::class.java)
      EasyMock
        .expect(packageManager.refreshAndGetPackages(false))
        .andReturn(packages.map { PyPackage(it.key, it.value) })

      EasyMock.replay(packageManager)
      return packageManager
    }

    override fun getManagementService(project: Project?, sdk: Sdk?) = throw UnsupportedOperationException()
    override fun clearCache(sdk: Sdk): Unit = throw UnsupportedOperationException()
  }
}