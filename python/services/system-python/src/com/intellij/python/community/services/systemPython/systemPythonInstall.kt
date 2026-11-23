// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.python.community.services.systemPython

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.community.services.systemPython.impl.PySystemPythonBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyVersionSpecifiers
import org.jetbrains.annotations.ApiStatus

private val logger = fileLogger()

/**
 * The newest system Python on the machine of [eelDescriptor] that [versionSpecifiers] allow. When there is none, it installs
 * one if the machine supports that and [confirmInstallation] agrees, and looks again.
 */
@ApiStatus.Internal
suspend fun getSystemPython(
  confirmInstallation: suspend () -> Boolean,
  pythonService: SystemPythonService,
  versionSpecifiers: PyVersionSpecifiers = PyVersionSpecifiers.ANY_SUPPORTED,
  eelDescriptor: EelDescriptor,
): Result<SystemPython, MessageError> {
  val eel = eelDescriptor.toEelApi()
  // First, find the latest python according to strategy
  var systemPythonBinary = pythonService.findSystemPythons(eel, forceRefresh = true).findMatchingPython(versionSpecifiers)

  // No python found?
  if (systemPythonBinary == null) {
    // Install it
    val installer = pythonService.getInstaller(eel)
                    ?: return PyResult.localizedError(PySystemPythonBundle.message("project.error.install.not.supported"))
    if (confirmInstallation()) {
      // Install
      when (val r = installer.installLatestPython(versionSpecifiers)) {
        is Result.Failure -> {
          val error = r.error
          logger.warn("Python installation failed $error")
          return PyResult.localizedError(PySystemPythonBundle.message("project.error.install.python", error))
        }
        is Result.Success -> {
          // Find the latest python again, after installation
          systemPythonBinary = pythonService.findSystemPythons(eel, forceRefresh = true).findMatchingPython(versionSpecifiers)
        }
      }
    }
  }

  return if (systemPythonBinary == null) {
    PyResult.localizedError(PySystemPythonBundle.message("project.error.all.pythons.bad"))
  }
  else {
    Result.Success(systemPythonBinary)
  }
}
