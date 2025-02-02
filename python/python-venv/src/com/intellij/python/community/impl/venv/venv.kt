// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.venv

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.HelperName
import com.intellij.python.community.execService.WhatToExec
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.failure
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.venvReader.Directory
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.CheckReturnValue
import java.nio.file.Path
import kotlin.io.path.pathString

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
  execService.execGetStdout(WhatToExec.Helper(python, helper = VIRTUALENV_ZIPAPP_NAME), args).getOr { return it }


  val venvPython = withContext(Dispatchers.IO) {
    envReader.findPythonInPythonRoot(venvDir)
  } ?: return failure(PyVenvBundle.message("py.venv.error.after.creation", venvDir))
  fileLogger().info("Venv created: $venvPython")

  withContext(Dispatchers.EDT) {
    PySdkSettings.instance.preferredVirtualEnvBaseSdk = python.pathString
  }
  return Result.success(venvPython)
}

// venv helper
@Internal
const val VIRTUALENV_ZIPAPP_NAME: HelperName = "virtualenv-20.24.5.pyz"