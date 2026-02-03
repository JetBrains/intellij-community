// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.common

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.remote.RemoteSdkException
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.getInterpreterVersionForJava
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class SdkCreationRequest {
  object LocalPython : SdkCreationRequest()
  data class RemotePython(val targetConfig: TargetEnvironmentConfiguration) : SdkCreationRequest()
}

/**
 * Creates sdk either local (you can choose type then) or remote (always vanilla)
 */
suspend fun PyEnvironmentFactory.createSdk(request: SdkCreationRequest): Pair<Sdk, AutoCloseable> = withContext(Dispatchers.IO) {
  when (request) {
    is SdkCreationRequest.LocalPython -> {
      val environment = createEnvironment(PredefinedPyEnvironments.VENV_3_12)
      val sdk = environment.prepareSdk()
      Pair(sdk, environment)
    }
    is SdkCreationRequest.RemotePython -> {
      val targetData = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance()),
                                                   request.targetConfig).apply {
        interpreterPath = PYTHON_PATH_ON_TARGET
      }
      try {
        requireNotNull(targetData.getInterpreterVersionForJava()) { "No $PYTHON_PATH_ON_TARGET on target" }
      }
      catch (e: RemoteSdkException) {
        throw RuntimeException("Error running $PYTHON_PATH_ON_TARGET", e)
      }
      Pair(ProjectJdkTable.getInstance().createSdk(PYTHON_PATH_ON_TARGET, PythonSdkType.getInstance()).apply {
        sdkModificator.apply {
          homePath = PYTHON_PATH_ON_TARGET
          sdkAdditionalData = targetData
          edtWriteAction {
            commitChanges()
          }
        }
      }, AutoCloseable { })
    }
  }
}

private const val PYTHON_PATH_ON_TARGET: FullPathOnTarget = "/usr/bin/python3"



