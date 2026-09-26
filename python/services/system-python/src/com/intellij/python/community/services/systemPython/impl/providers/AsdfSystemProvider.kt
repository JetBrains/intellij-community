// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl.providers

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.getOrNull
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.intellij.python.community.services.systemPython.icons.PythonCommunityServicesSystemPythonIcons
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException


internal class AsdfSystemPythonProvider : SystemPythonProvider {
  private companion object {
    private val LOGGER: Logger = Logger.getInstance(AsdfSystemPythonProvider::class.java)
  }

  override suspend fun findSystemPythons(eelApi: EelApi): PyResult<Set<PythonBinary>> {
    val pythons = withContext(Dispatchers.IO) {
      val env = try {
        eelApi.exec.fetchLoginShellEnvVariables()
      }
      catch (e: IOException) {
        LOGGER.warn("Failed to fetch login shell env variables for asdf discovery", e)
        return@withContext emptySet()
      }
      val rawAsdfRoot = env["ASDF_DATA_DIR"]?.takeIf { it.isNotBlank() }
      val asdfRoot = if (rawAsdfRoot != null) {
        try {
          EelPath.parse(rawAsdfRoot, eelApi.descriptor)
        }
        catch (e: EelPathException) {
          LOGGER.warn("ASDF_DATA_DIR='$rawAsdfRoot' is not a valid ${eelApi.descriptor.osFamily} absolute path; skipping asdf discovery", e)
          return@withContext emptySet()
        }
      }
      else {
        eelApi.userInfo.home.resolve(".asdf")
      }

      val versionsDir = asdfRoot.resolve("installs").resolve("python")
      val entries = eelApi.fs.listDirectory(versionsDir)
        .getOrNull()

      if (entries == null) {
        return@withContext emptySet()
      }

      val paths = entries
        .map { versionsDir.resolve(it).resolve("bin").asNioPath() }

      return@withContext collectPythonsInPaths(paths, listOf(python3NamePattern))
    }

    return PyResult.success(pythons)
  }

  override val uiCustomization: PyToolUIInfo?
    get() {
      // TODO: proper icon
      return PyToolUIInfo(toolName = "asdf", icon = PythonCommunityServicesSystemPythonIcons.Asdf)
    }
}