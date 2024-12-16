// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.remote.RemoteSdkException
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualFile
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.getInterpreterVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assume

/**
 * To be used with [createSdk]
 */
sealed class SdkCreationRequest {
  data class LocalPython(val pythonType: PythonType<*> = PythonType.VanillaPython3) : SdkCreationRequest()
  data class RemotePython(val targetConfig: TargetEnvironmentConfiguration) : SdkCreationRequest()
}

/**
 * Creates sdk either local (you can choose type then) or remote (always vanilla)
 */
suspend fun createSdk(request: SdkCreationRequest): Pair<Sdk, AutoCloseable> = withContext(Dispatchers.IO) {
  when (request) {
    is SdkCreationRequest.LocalPython -> {
      when (val pythonType = request.pythonType) {
        is PythonType.Conda -> {
          val (env, closable) = pythonType.getTestEnvironment().getOrThrow()
          Pair(env.createSdkFromThisEnv(null, emptyList()), closable)
        }
        is PythonType.VanillaPython3 -> {
          val (python, closable) = pythonType.getTestEnvironment().getOrThrow()
          Pair(SdkConfigurationUtil.setupSdk(emptyArray(), python.refreshAndGetVirtualFile(),
                                             PythonSdkType.getInstance(), null, null), closable)
        }
      }
    }
    is SdkCreationRequest.RemotePython -> {
      val targetData = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance()),
                                                   request.targetConfig).apply {
        interpreterPath = PYTHON_PATH_ON_TARGET
      }
      try {
        Assume.assumeNotNull("No $PYTHON_PATH_ON_TARGET on target", targetData.getInterpreterVersion(null, true))
      }
      catch (e: RemoteSdkException) {
        Assume.assumeNoException("Error running $PYTHON_PATH_ON_TARGET", e)
      }
      Pair(ProjectJdkTable.getInstance().createSdk(PYTHON_PATH_ON_TARGET, PythonSdkType.getInstance()).apply {
        sdkModificator.apply {
          homePath = PYTHON_PATH_ON_TARGET
          sdkAdditionalData = targetData
          writeAction {
            commitChanges()
          }
        }
      }, AutoCloseable { })
    }
  }
}

private const val PYTHON_PATH_ON_TARGET: FullPathOnTarget = "/usr/bin/python3"



