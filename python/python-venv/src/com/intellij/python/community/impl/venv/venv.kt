// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.venv

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.HelperName
import com.intellij.python.community.execService.WhatToExec
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.failure
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.validatePythonAndGetVersion
import com.jetbrains.python.venvReader.Directory
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.CheckReturnValue
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

/**
 * Create virtual env in [venvDir] using [python].
 * If [python] resides on remote eel, [VIRTUALENV_ZIPAPP_NAME] helper will be copied there.
 * [inheritSitePackages] inherits [python] packages into newly created virtual env.
 * @return either error or [PythonBinary] path of a python binary in newly created virtual env.
 */
@Internal
@CheckReturnValue
suspend fun createVenv(
  python: PythonBinary,
  venvDir: Directory,
  inheritSitePackages: Boolean = false,
  envReader: VirtualEnvReader = VirtualEnvReader.Instance,
): Result<Path, PyError> {
  val execService = ExecService()
  val args = buildList {
    if (inheritSitePackages) {
      add("--system-site-packages")
    }
    add(venvDir.pathString)
  }
  val version = python.validatePythonAndGetVersion().getOr { return failure(it.error) }
  val helper = if (version.isPy3K) VIRTUALENV_ZIPAPP_NAME else PY_2_VIRTUALENV_ZIPAPP_NAME
  execService.execGetStdout(WhatToExec.Helper(python, helper = helper), args, ExecOptions(timeout = 3.minutes)).getOr { return it }


  val venvPython = withContext(Dispatchers.IO) {
    envReader.findPythonInPythonRoot(venvDir)
  } ?: return failure(PyVenvBundle.message("py.venv.error.after.creation", venvDir))
  fileLogger().info("Venv created: $venvPython")

  withContext(Dispatchers.EDT) {
    PySdkSettings.instance.preferredVirtualEnvBaseSdk = python.pathString
  }
  // A new venv was just created, we need to clear cache to make sure it isn't marked as "broken" to prevent inspections
  PythonSdkFlavor.clearExecutablesCache()
  return Result.success(venvPython)
}

// venv helper, update from https://bootstrap.pypa.io/virtualenv.pyz
@Internal
const val VIRTUALENV_ZIPAPP_NAME: HelperName = "virtualenv-py3.pyz"

// Ancient version, the last one compatible with Py2
private const val PY_2_VIRTUALENV_ZIPAPP_NAME = "virtualenv-20.13.0.pyz"