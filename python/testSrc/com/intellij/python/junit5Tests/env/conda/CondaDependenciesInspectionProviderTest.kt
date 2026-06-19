// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.conda

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.dependencies.DependenciesInspection
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.packaging.management.RequirementsProviderType
import com.jetbrains.python.packaging.management.TestPythonPackageManager
import com.jetbrains.python.requirements.PythonDependencyTestCase
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.runBlocking

@TestDataPath($$"$CONTENT_ROOT/../testData/conda/environmentYml/inspections")
class CondaDependenciesInspectionProviderTest : PythonDependencyTestCase() {

  fun testCondaDependenciesInspectionProvider() {
    val provider =
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
        )
    initTestPackageManager(provider)

    myFixture.copyDirectoryToProject(getTestName(false), "")
    markEnvironmentYmlAsDependencyRoot()
    myFixture.configureFromTempProjectFile("environment.yml")

    myFixture.enableInspections(DependenciesInspection::class.java)
    myFixture.checkHighlighting(true, false, true, false)

    val djangoOffset = myFixture.editor.document.text.indexOf("django=1.3.1")
    myFixture.editor.caretModel.moveToOffset(djangoOffset)
    val updateFixName = PyBundle.message("QFIX.NAME.update.requirement", "django")
    assertContainsElements(myFixture.availableIntentions.map { it.text }, updateFixName)
  }

  override fun setUp() {
    super.setUp()
    InspectionProfileImpl.INIT_INSPECTIONS = true
    myFixture.project.pythonSdk = projectDescriptor.sdk
  }

  override fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
    super.tearDown()
  }

  override fun getBasePath(): String = "/community/python/testData/conda/environmentYml/inspections/"

  /**
   * Marks the SDK as tracking `environment.yml` as its declared-dependencies root (associating it with
   * the module dir that holds the file) so the package manager's dependency-file cache tracks it — the
   * gate in [com.jetbrains.python.inspections.dependencies.DependenciesInspection] reads that cache. The
   * real conda manager exposes `environment.yml` as its root unconditionally; the test manager keys this
   * off SDK user data. Init is then forced so the cache is seeded before highlighting runs.
   */
  private fun markEnvironmentYmlAsDependencyRoot() {
    val sdk = myFixture.project.pythonSdk!!
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.ENVIRONMENT_YML)
    val moduleDir = myFixture.findFileInTempDir("environment.yml").parent
    ApplicationManager.getApplication().runWriteAction {
      val modificator = sdk.sdkModificator
      (modificator.sdkAdditionalData as PythonSdkAdditionalData).associatedModulePath = moduleDir.path
      modificator.commitChanges()
    }
    runBlocking { PythonPackageManager.forSdk(myFixture.project, sdk).waitForInit() }
  }
}
