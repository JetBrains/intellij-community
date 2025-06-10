// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl.providers

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.getOrNull
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.python.community.services.shared.UICustomization
import com.intellij.python.community.services.systemPython.PythonCommunityServicesSystemPythonIcons
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private class PyenvSystemPythonProvider : SystemPythonProvider {
  private val LOGGER: Logger = Logger.getInstance(PyenvSystemPythonProvider::class.java)

  override suspend fun findSystemPythons(eelApi: EelApi): PyResult<Set<PythonBinary>> {
    if (useLegacyPythonProvider()) {
      return PyResult.success(emptySet())
    }

    val pythons = withContext(Dispatchers.IO) {
      try {
        val env = eelApi.exec.fetchLoginShellEnvVariables()
        val pyenvRoot = if ("PYENV_ROOT" in env) {
          EelPath.parse(env["PYENV_ROOT"]!!, eelApi.descriptor)
        }
        else {
          eelApi.userInfo.home.resolve(".pyenv")
        }

        val versionsDir = pyenvRoot.resolve("versions")
        val entries = eelApi.fs.listDirectory(versionsDir)
          .getOrNull()

        if (entries == null) {
          return@withContext emptySet<PythonBinary>()
        }

        val paths = entries
          .map { versionsDir.resolve(it).resolve("bin").asNioPath() }

        return@withContext collectPythonsInPaths(eelApi, paths, listOf(python3NamePattern))
      }
      catch (e: RuntimeException) {
        LOGGER.error("failed to discover pyenv pythons", e)
      }

      return@withContext emptySet()
    }

    return PyResult.success(pythons)
  }

  override val uiCustomization: UICustomization?
    get() {
      // TODO: proper icon
      return UICustomization(title = "pyenv", icon = PythonCommunityServicesSystemPythonIcons.Pyenv)
    }
}
