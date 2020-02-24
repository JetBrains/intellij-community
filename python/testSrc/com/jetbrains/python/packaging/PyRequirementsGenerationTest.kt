// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.registerServiceInstance
import com.jetbrains.python.fixtures.PyTestCase
import org.easymock.EasyMock

class PyRequirementsGenerationTest : PyTestCase() {

  var oldPackageManagers: PyPackageManagers? = null

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

  private fun doTest(versionSpecifier: PyRequirementsVersionSpecifierType = PyRequirementsVersionSpecifierType.STRONG_EQ,
                     removeUnused: Boolean = false,
                     modifyBaseFiles: Boolean = false,
                     block: ((PyRequirementsAnalysisResult) -> Unit)? = null) {
    val settings = PyPackageRequirementsSettings.getInstance(myFixture.module)
    val oldRequirementsPath = settings.requirementsPath
    val oldVersionSpecifier = settings.versionSpecifier
    val oldRemoveUnused = settings.removeUnused
    val oldModifyBaseFiles = settings.modifyBaseFiles

    try {
      overrideInstalledPackages(
        "Django" to "3.0.0",
        "requests" to "2.22.0",
        "Jinja2" to "2.11.1",
        "pandas" to "1.0.1" ,
        "cookiecutter" to "1.7.0",
        "numpy" to "1.18.1",
        "tox" to "3.14.4"
      )

      settings.requirementsPath = "requirements.txt"
      settings.versionSpecifier = versionSpecifier
      settings.removeUnused = removeUnused
      settings.modifyBaseFiles = modifyBaseFiles

      val testName = getTestName(true)
      myFixture.copyDirectoryToProject(testName, "")
      myFixture.configureFromTempProjectFile(settings.requirementsPath)
      if (block != null) {
        block(prepareRequirementsText(myFixture.module, settings)!!)
      }
      else {
        syncWithImports(myFixture.module)
        myFixture.checkResultByFile("$testName/new_${settings.requirementsPath}")
      }
    }
    finally {
      settings.requirementsPath = oldRequirementsPath
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

  private fun overrideInstalledPackages(vararg packages: Pair<String, String>) {
    ApplicationManager.getApplication().registerServiceInstance(PyPackageManagers::class.java, MockPyPackageManagers(packages.toMap()))
  }

  private class MockPyPackageManagers(val packages: Map<String, String>) : PyPackageManagers() {
    override fun forSdk(sdk: Sdk): PyPackageManager {
      val packageManager = EasyMock.createMock<PyPackageManager>(PyPackageManager::class.java)
      EasyMock
        .expect(packageManager.packages)
        .andReturn(packages.map { PyPackage(it.key, it.value, null, emptyList()) })

      EasyMock.replay(packageManager)
      return packageManager
    }

    override fun getManagementService(project: Project?, sdk: Sdk?) = throw UnsupportedOperationException()
    override fun clearCache(sdk: Sdk): Unit = throw UnsupportedOperationException()
  }
}