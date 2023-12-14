// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.application.*
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.remote.RemoteSdkException
import com.jetbrains.env.PyEnvTestSettings
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.WinPythonSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.getInterpreterVersion
import org.junit.Assume
import org.junit.rules.ExternalResource

private const val PYTHON_PATH_ON_TARGET: FullPathOnTarget = "/usr/bin/python3"

/**
 * Creates python SDK either local (if [targetConfigProducer] is null) or target.
 * In case of target, it should have [PYTHON_PATH_ON_TARGET]
 * Locals are search automatically like in [PyEnvTestSettings] or using [com.jetbrains.python.sdk.flavors.PythonSdkFlavor.suggestLocalHomePaths]
 */
class PySDKRule(private val targetConfigProducer: (() -> TargetEnvironmentConfiguration)?) : ExternalResource() {
  @Volatile
  lateinit var sdk: Sdk
    private set

  override fun before() {
    val targetConfig = targetConfigProducer?.let { it() }
    val (pythonPath, additionalData) = if (targetConfig == null) {
      // Local
      val flavor = if (SystemInfo.isWindows) WinPythonSdkFlavor() else UnixPythonSdkFlavor.getInstance()
      val pythonPath = flavor.suggestLocalHomePaths(null, null).firstOrNull()
                       ?: PyEnvTestSettings().pythons.firstOrNull()?.toPath()?.let { PythonSdkUtil.getPythonExecutable(it.toString()); }
      Assume.assumeNotNull("No python found on local installation", pythonPath)
      Pair(pythonPath!!.toString(), PythonSdkAdditionalData(PyFlavorAndData(PyFlavorData.Empty, flavor)))
    }
    else {
      // Target
      val targetData = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance()),
                                                   targetConfig).apply {
        interpreterPath = PYTHON_PATH_ON_TARGET
      }
      try {
        Assume.assumeNotNull("No $PYTHON_PATH_ON_TARGET on target", targetData.getInterpreterVersion(null, true))
      }
      catch (e: RemoteSdkException) {
        Assume.assumeNoException("Error running $PYTHON_PATH_ON_TARGET", e)
      }
      Pair(PYTHON_PATH_ON_TARGET, targetData) //No ability to look for remote pythons for now
    }

    sdk = ProjectJdkTable.getInstance().createSdk(pythonPath, PythonSdkType.getInstance())
    val sdkModificator = sdk.sdkModificator
    sdkModificator.homePath = pythonPath
    sdkModificator.sdkAdditionalData = additionalData
    val application = ApplicationManager.getApplication()
    application.invokeAndWait {
      application.runWriteAction {
        sdkModificator.commitChanges()
      }
    }
  }
}
