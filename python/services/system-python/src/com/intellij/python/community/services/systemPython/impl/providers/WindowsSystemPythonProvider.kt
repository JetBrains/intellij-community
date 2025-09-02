// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl.providers

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.WinRegistryService
import com.jetbrains.python.sdk.getAppxFiles
import com.jetbrains.python.venvReader.tryResolvePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.collections.iterator
import kotlin.io.path.exists
import kotlin.io.path.pathString


class WindowsSystemPythonProvider(val winRegistryBase: WinRegistryService? = null) : SystemPythonProvider {
  private val LOGGER: Logger = Logger.getInstance(WindowsSystemPythonProvider::class.java)

  private val names = listOf(
    "pypy.exe",
    "python.exe")

  // Registry roots and product mappings from WinPythonSdkFlavor
  private val REG_ROOTS = arrayOf("HKEY_LOCAL_MACHINE", "HKEY_CURRENT_USER")
  private val REGISTRY_MAP = mapOf(
    "Python" to "python.exe",
    "IronPython" to "ipy.exe"
  )
  val winRegistry: Lazy<WinRegistryService> = lazy {
    winRegistryBase ?: ApplicationManager.getApplication().getService(WinRegistryService::class.java)
  }

  // Windows Store Python product name
  private val APPX_PRODUCT = "Python"
  private val pythonVersionedExePattern = Pattern.compile("python[0-9.]*?\\.exe$")

  override suspend fun findSystemPythons(eelApi: EelApi): PyResult<Set<PythonBinary>> {
    if (eelApi != localEel || !eelApi.platform.isWindows || useLegacyPythonProvider()) {
      return PyResult.success(emptySet())
    }

    val pythons = withContext(Dispatchers.IO) {
      try {
        val candidates = mutableSetOf<Path>()

        for (name in names) {
          val binaries = PathEnvironmentVariableUtil.findAllExeFilesInPath(name)
            .mapNotNull { it.toPath() }
            .filter { !PythonSdkUtil.isConda(it.pathString) }
            .toSet()

          candidates.addAll(binaries)
        }

        candidates.addAll(getPythonsFromStore())
        candidates.addAll(getPythonsFromRegistry())

        return@withContext candidates
      }
      catch (e: RuntimeException) {
        LOGGER.error("Failed to discover Windows system pythons", e)
      }

      return@withContext emptySet<PythonBinary>()
    }

    return PyResult.success(pythons)
  }

  // Check https://www.python.org/dev/peps/pep-0514/ for windows registry layout to understand
  private fun getPythonsFromRegistry(): Set<Path> {
    val candidates = mutableSetOf<Path>()

    for (regRoot in REG_ROOTS) {
      for ((productId, exe) in REGISTRY_MAP) {
        val companiesPath = "$regRoot\\SOFTWARE\\$productId"
        val companiesPathWow = "$regRoot\\SOFTWARE\\Wow6432Node\\$productId"

        for (path in arrayOf(companiesPath, companiesPathWow)) {
          for (company in winRegistry.value.listBranches(path)) {
            val pathToCompany = "$path\\$company"

            for (version in winRegistry.value.listBranches(pathToCompany)) {
              val folder = winRegistry.value.getDefaultKey("$pathToCompany\\$version\\InstallPath")
              tryResolvePath(folder)
                ?.resolve(exe)
                ?.takeIf(Path::exists)
                ?.let { candidates.add(it) }
            }
          }
        }
      }
    }

    return candidates
  }

  private fun getPythonsFromStore(): Set<Path> {
    return try {
      getAppxFiles(APPX_PRODUCT, pythonVersionedExePattern.toRegex())
        .map { it.toAbsolutePath() }
        .toSet()
    }
    catch (e: Exception) {
      LOGGER.debug("Error getting Python from Windows Store", e)
      emptySet()
    }
  }
}
