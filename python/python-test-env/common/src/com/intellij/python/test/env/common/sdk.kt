// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.common

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.remote.RemoteSdkException
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.getInterpreterVersionForJava
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

sealed class SdkCreationRequest {
  object LocalPython : SdkCreationRequest()
  data class RemotePython(val targetConfig: TargetEnvironmentConfiguration) : SdkCreationRequest()
}

/**
 * Adds an interpreter to the SDK table for an old test without a `PyProject`, either local or remote (always
 * vanilla). The env dir, or the working dir on the target, is its owner. Closing the returned [AutoCloseable] removes it.
 */
suspend fun PyEnvironmentFactory.createSdk(project: Project, request: SdkCreationRequest): Pair<PythonInterpreter, AutoCloseable> = withContext(Dispatchers.IO) {
  val registry = PythonInterpreterRegistry.getInstance(project)
  when (request) {
    is SdkCreationRequest.LocalPython -> {
      val environment = createEnvironment(PredefinedPyEnvironments.VENV_3_12)
      val interpreter = registry.addTestInterpreter(environment.prepareSdk())
      Pair(interpreter, AutoCloseable {
        runBlocking { registry.removeTestInterpreter(interpreter) }
        environment.close()
      })
    }
    is SdkCreationRequest.RemotePython -> {
      val targetData = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance()), workingDir, request.targetConfig).apply {
        interpreterPath = PYTHON_PATH_ON_TARGET
      }
      try {
        requireNotNull(targetData.getInterpreterVersionForJava()) { "No $PYTHON_PATH_ON_TARGET on target" }
      }
      catch (e: RemoteSdkException) {
        throw RuntimeException("Error running $PYTHON_PATH_ON_TARGET", e)
      }
      val built = registry.newPythonInterpreter(workingDir, PathHolder.Target(PYTHON_PATH_ON_TARGET), targetData, PYTHON_PATH_ON_TARGET).getOrThrow()
      val interpreter = registry.addTestInterpreter(built)
      Pair(interpreter, AutoCloseable { runBlocking { registry.removeTestInterpreter(interpreter) } })
    }
  }
}

private const val PYTHON_PATH_ON_TARGET: FullPathOnTarget = "/usr/bin/python3"



