// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pythonPackageManager

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.env.EnvTestTagsRequired
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyExecutionFixtureTestTask
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonSimplePackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.pip.PipManagementInstaller
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.statistics.version
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test


class PythonPackageManagerManagementInstallationTest : PyEnvTestCase() {

  companion object {
    private val PKG_TO_INSTALL = PythonSimplePackageSpecification("pytest", null, null)
  }

  @EnvTestTagsRequired(tags = ["python3.8"])
  @Test
  fun testManagementInstallation38() {
    runPythonTest(PythonPackageManagerManagementInstallationTask(PKG_TO_INSTALL))
  }

  @EnvTestTagsRequired(tags = ["python3.9"])
  @Test
  fun testManagementInstallation39() {
    runPythonTest(PythonPackageManagerManagementInstallationTask(PKG_TO_INSTALL))
  }

  @EnvTestTagsRequired(tags = ["python3.10"])
  @Test
  fun testManagementInstallation310() {
    runPythonTest(PythonPackageManagerManagementInstallationTask(PKG_TO_INSTALL))
  }

  @EnvTestTagsRequired(tags = ["python3.11"])
  @Test
  fun testManagementInstallation311() {
    runPythonTest(PythonPackageManagerManagementInstallationTask(PKG_TO_INSTALL))
  }

  @EnvTestTagsRequired(tags = ["python3.12"])
  @Test
  fun testManagementInstallation312() {
    runPythonTest(PythonPackageManagerManagementInstallationTask(PKG_TO_INSTALL))
  }
}

open class PythonPackageManagerManagementInstallationTask(private val pkgToInstall: PythonSimplePackageSpecification) : PyExecutionFixtureTestTask("") {

  override fun runTestOn(sdkHome: String, existingSdk: Sdk?) {
    requireNotNull(existingSdk) { "Sdk should be not bull"}

    val sdk = existingSdk
    val manager = PythonPackageManager.forSdk(myFixture.project, sdk)
    val managementInstaller = PipManagementInstaller(sdk, manager)

    runBlocking {
      manager.reloadPackages()
      uninstallManagement(manager)

      if (sdk.version > LanguageLevel.PYTHON27) {
        assertTrue("Management packages should be uninstalled", !managementInstaller.hasManagement())
      }

      installPackage(manager, pkgToInstall)
      assertTrue("Management packages should be installed", managementInstaller.hasManagement())
    }
  }

  private suspend fun uninstallManagement(manager: PythonPackageManager) {
    manager.uninstallPackage(PythonPackage(PyPackageUtil.PIP, EMPTY_STRING, false))
    manager.uninstallPackage(PythonPackage(PyPackageUtil.SETUPTOOLS, EMPTY_STRING, false))
  }

  private suspend fun installPackage(manager: PythonPackageManager, spec: PythonSimplePackageSpecification) {
    manager.installPackage(spec, emptyList())
  }

  companion object {
    private const val EMPTY_STRING = ""
  }
}