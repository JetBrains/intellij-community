// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pythonPackageManager

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkTypeId
import com.jetbrains.env.EnvTestTagsRequired
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyExecutionFixtureTestTask
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.hasInstalledPackage
import com.jetbrains.python.packaging.management.toInstallRequest
import com.jetbrains.python.packaging.repository.PyPIPackageRepository
import com.jetbrains.python.sdk.PythonSdkType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test


class PythonPackageManagerNullAdditionalDataTest : PyEnvTestCase() {
  companion object {
    private val PKG = PythonRepositoryPackageSpecification(PyPIPackageRepository, "requests")
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


class PythonPackageManagerNullAdditionalDataTask(private val pkg: PythonRepositoryPackageSpecification) : PyExecutionFixtureTestTask("") {

  private suspend fun createSdkWithNullAdditionalData(sdkTypeId: SdkTypeId, homePath: String, existingSdk: Sdk): Sdk {
    val sdkTable = ProjectJdkTable.getInstance()

    return writeAction {
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

    runBlocking {
      val configuredSdk = createSdkWithNullAdditionalData(pythonSdkType, sdkHome, existingSdk)
      val manager = PythonPackageManager.forSdk(myFixture.project, configuredSdk)

      manager.installPackage(pkg.toInstallRequest(), emptyList())
      assertTrue("Package should be installed", manager.hasInstalledPackage(pkg.name))

      manager.uninstallPackage(pkg.name)
      assertTrue("Package should be uninstalled", !manager.hasInstalledPackage(pkg.name))
    }
  }

  companion object {
    private const val SDK_NAME = "Python Null Additional Data"
  }
}