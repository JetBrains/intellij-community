// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.util.EnvironmentUtil
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<PySdkFromEnvironmentVariable>()

@ApiStatus.Internal
object PySdkFromEnvironmentVariable {
  const val PYCHARM_PYTHON_PATH_PROPERTY: String = "PycharmPythonPath"
  const val PYCHARM_PYTHON_PATH_ENV: String = "PYCHARM_PYTHON_PATH"

  fun getPycharmPythonPathProperty(): String? {
    // see https://www.jetbrains.com/help/pycharm/configure-an-interpreter-using-command-line.html
    return System.getProperty(PYCHARM_PYTHON_PATH_PROPERTY) ?: EnvironmentUtil.getValue(PYCHARM_PYTHON_PATH_ENV)
  }

  fun findOrCreateSdkByPath(path: String): Sdk? {
    EDT.assertIsEdt()
    return findByPath(path) ?: createSdkByPath(path)
  }

  fun setModuleSdk(module: Module, projectSdk: Sdk?, sdk: Sdk, pythonPath: String) {
    EDT.assertIsEdt()
    val moduleSdk = PythonSdkUtil.findPythonSdk(module)
    if (pythonPath != projectSdk?.homePath || pythonPath != moduleSdk?.homePath) {
      switchToSdk(module, sdk, moduleSdk)
    }
  }
}

private fun findByPath(pycharmPythonPathEnvVariable: String): Sdk? {
  val sdkType = PythonSdkType.getInstance()
  val sdks = ProjectJdkTable.getInstance().getSdksOfType(sdkType)
  val sdk = SdkConfigurationUtil.findByPath(sdkType, sdks.toTypedArray(), pycharmPythonPathEnvVariable)

  if (sdk != null) {
    LOG.info("Found a previous sdk")
  }

  return sdk
}

private fun createSdkByPath(pycharmPythonPathEnvVariable: String): Sdk? {
  val sdk = SdkConfigurationUtil.createAndAddSDK(pycharmPythonPathEnvVariable, PythonSdkType.getInstance())
  if (sdk != null) {
    LOG.info("No suitable sdk found, created a new one")
  }
  return sdk
}