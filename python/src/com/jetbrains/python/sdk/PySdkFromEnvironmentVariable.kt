// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.util.EnvironmentUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.pathString

private val LOG = logger<PySdkFromEnvironmentVariable>()

@ApiStatus.Internal
object PySdkFromEnvironmentVariable {
  const val PYCHARM_PYTHON_PATH_PROPERTY: String = "PycharmPythonPath"
  const val PYCHARM_PYTHON_PATH_ENV: String = "PYCHARM_PYTHON_PATH"

  fun getPycharmPythonPathProperty(): String? {
    // see https://www.jetbrains.com/help/pycharm/configure-an-interpreter-using-command-line.html
    return System.getProperty(PYCHARM_PYTHON_PATH_PROPERTY) ?: EnvironmentUtil.getValue(PYCHARM_PYTHON_PATH_ENV)
  }

  suspend fun findOrCreateSdkByPath(path: String, moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    val path = try {
      Path.of(path)
    }
    catch (e: InvalidPathException) {
      return PyResult.failure(MessageError(PyBundle.message("sdk.bad.python", path, e)))
    }
    return findByPath(path)?.let { PyResult.success(it) } ?: createLocalSdkGuessingTypeByPath(path, moduleOrProject)
  }

  suspend fun setModuleSdk(module: Module, projectSdk: Sdk?, sdk: Sdk, pythonPath: String) {
    val moduleSdk = PythonSdkUtil.findPythonSdk(module)
    if (pythonPath != projectSdk?.homePath || pythonPath != moduleSdk?.homePath) {
      writeAction {
        module.pythonSdk = sdk
      }
    }
  }
}

private suspend fun findByPath(pycharmPythonPathEnvVariable: PythonBinary): Sdk? {
  val sdkType = PythonSdkType.getInstance()
  val sdks = ProjectJdkTable.getInstance().getSdksOfType(sdkType)
  val sdk =
    withContext(Dispatchers.IO) { SdkConfigurationUtil.findByPath(sdkType, sdks.toTypedArray(), pycharmPythonPathEnvVariable.pathString) }

  if (sdk != null) {
    LOG.info("Found a previous sdk")
  }

  return sdk
}
