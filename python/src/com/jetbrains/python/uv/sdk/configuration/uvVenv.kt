// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.sdk.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.common.tools.ToolId
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.findEnvOrNull
import com.jetbrains.python.sdk.configuration.getSdkAssociatedModule
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.detectPythonEnvironment
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.sdk.uv.impl.getUvExecutableLocal
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.withProject
import com.jetbrains.python.venvReader.tryResolvePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.collections.contains

private val logger = fileLogger()

internal suspend fun checkManageableUvEnvBase(
  venvsInModule: List<PythonBinary>,
): EnvCheckerResult {
  getUvExecutableLocal() ?: return EnvCheckerResult.CannotConfigure
  val intentionName = PyBundle.message("sdk.set.up.uv.environment")
  val envFound = getUvEnv(venvsInModule)?.findEnvOrNull(intentionName)
  return envFound ?: EnvCheckerResult.EnvNotFound(intentionName)
}

internal suspend fun createUvSdk(module: Module, toolId: ToolId, venvsInModule: List<PythonBinary>, envExists: Boolean): PyResult<Sdk> {
  val uv = getUvExecutableLocal() ?: return PyResult.localizedError(PyBundle.message("sdk.cannot.find.uv.executable"))
  val sdkAssociatedModule = module.getSdkAssociatedModule(toolId)
  val workingDir: Path? = tryResolvePath(sdkAssociatedModule.baseDir?.path)
  if (workingDir == null) {
    throw IllegalStateException("Can't determine working dir for the module")
  }

  val errorSink = ErrorSink().withProject(sdkAssociatedModule.project)
  val sdkSetupResult = if (envExists) {
    getUvEnv(venvsInModule)?.let {
      setupExistingEnvAndSdk(it, uv, workingDir, false)
    } ?: run {
      logger.warn("Can't find existing uv environment in project, but it was expected. " +
                  "Probably it was deleted. New environment will be created")
      setupNewUvSdkAndEnv(uv, workingDir, null, errorSink)
    }
  }
  else setupNewUvSdkAndEnv(uv, workingDir, null, errorSink)

  sdkSetupResult.onSuccess {
    withContext(Dispatchers.EDT) {
      it.setAssociationToModule(sdkAssociatedModule)
    }
  }
  return sdkSetupResult
}

private fun getUvEnv(venvsInModule: List<PythonBinary>): PythonBinary? = venvsInModule.firstOrNull { it.isUvEnv() }

internal fun PythonBinary.isUvEnv(): Boolean {
  return detectPythonEnvironment().successOrNull?.let { it is PythonEnvironment.Venv && "uv" in it.config } == true
}