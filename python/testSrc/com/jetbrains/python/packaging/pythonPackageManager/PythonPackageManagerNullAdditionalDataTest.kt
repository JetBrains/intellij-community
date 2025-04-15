// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pythonPackageManager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkTypeId
import com.jetbrains.env.EnvTestTagsRequired
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyExecutionFixtureTestTask
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonSimplePackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.PythonSdkType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test


class PythonPackageManagerNullAdditionalDataTest : PyEnvTestCase() {

  companion object {
    private val PKG = PythonSimplePackageSpecification("requests", null, null)
  }

  @EnvTestTagsRequired(tags = ["python3.8"])
  @Test
  fun testInstallUninstallPackageWithNullAdditionalDataPython38() {
    runPythonTest(PythonPackageManagerNullAdditionalDataTask(PKG))
  }

  @EnvTestTagsRequired(tags = ["python3.9"])
  @Test
  fun testInstallUninstallPackageWithNullAdditionalDataPython39() {
    runPythonTest(PythonPackageManagerNullAdditionalDataTask(PKG))
  }

  @EnvTestTagsRequired(tags = ["python3.10"])
  @Test
  fun testInstallUninstallPackageWithNullAdditionalDataPython310() {
    runPythonTest(PythonPackageManagerNullAdditionalDataTask(PKG))
  }

  @EnvTestTagsRequired(tags = ["python3.11"])
  @Test
  fun testInstallUninstallPackageWithNullAdditionalDataPython311() {
    runPythonTest(PythonPackageManagerNullAdditionalDataTask(PKG))
  }

  @EnvTestTagsRequired(tags = ["python3.12"])
  @Test
  fun testInstallUninstallPackageWithNullAdditionalDataPython312() {
    runPythonTest(PythonPackageManagerNullAdditionalDataTask(PKG))
  }
}


class PythonPackageManagerNullAdditionalDataTask(private val pkg: PythonSimplePackageSpecification) : PyExecutionFixtureTestTask("") {

  private fun createSdkWithNullAdditionalData(sdkTypeId: SdkTypeId, homePath: String, existingSdk: Sdk): Sdk {
    val sdkTable = ProjectJdkTable.getInstance()

    return ApplicationManager.getApplication().runWriteAction<Sdk> {
      val newSdk = sdkTable.createSdk(SDK_NAME, sdkTypeId)
      configureSdk(newSdk, homePath, existingSdk)
      newSdk
    }
  }

  private fun configureSdk(sdk: Sdk, homePath: String, existingSdk: Sdk) {
    val modificator: SdkModificator = sdk.sdkModificator
    modificator.apply {
      this.homePath = homePath
      this.versionString = existingSdk.versionString
      this.sdkAdditionalData = null
      commitChanges()
    }
  }

  override fun runTestOn(sdkHome: String, existingSdk: Sdk?) {
    requireNotNull(existingSdk) { "Sdk should be not bull" }

    val pythonSdkType = PythonSdkType.getInstance()
    val configuredSdk = createSdkWithNullAdditionalData(pythonSdkType, sdkHome, existingSdk)
    val manager = PythonPackageManager.forSdk(myFixture.project, configuredSdk)

    runBlocking {
      manager.installPackage(pkg, emptyList(), withBackgroundProgress = false)
      assertTrue("Package should be installed", manager.installedPackages.map { it.name }.contains(pkg.name))

      manager.uninstallPackage(PythonPackage(pkg.name, pkg.version.orEmpty(), false))
      assertTrue("Package should be uninstalled", !manager.installedPackages.map { it.name }.contains(pkg.name))
    }
  }

  companion object {
    private const val SDK_NAME = "Python Null Additional Data"
  }
}