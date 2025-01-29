// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.*
import com.intellij.openapi.components.Service.Level.APP
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.impl.installer.PySdkToInstallManager
import com.intellij.python.community.services.internal.impl.PythonWithLanguageLevelImpl
import com.intellij.python.community.services.systemPython.SystemPythonServiceImpl.MyServiceState
import com.intellij.python.community.services.systemPython.spi.SystemPythonProvider
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.installer.installBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Service to register and obtain [SystemPython]s
 */
@ApiStatus.NonExtendable
sealed interface SystemPythonService {
  /**
   * System pythons installed on OS.
   * Sort pythons by [SystemPython.languageLevel] to find the highest one
   */
  suspend fun findSystemPythons(eelApi: EelApi = localEel): Set<SystemPython>

  /**
   * When user provides a path to the python binary, use this method to the [SystemPython].
   * @return either [SystemPython] or an error if python is broken.
   */
  suspend fun registerSystemPython(pythonPath: PythonBinary): Result<SystemPython, @Nls String>

  /**
   * @return tool to install python on OS If [eelApi] supports python installation
   */
  fun getInstaller(eelApi: EelApi = localEel): PythonInstallerService?
}

/**
 * Creates an instance of this service
 */
fun SystemPythonService(): SystemPythonService = ApplicationManager.getApplication().service<SystemPythonServiceImpl>()

// Implementation

@Service(APP)
@State(name = "SystemPythonService", storages = [Storage("systemPythonService.xml", roamingType = RoamingType.LOCAL)], allowLoadInTests = true)
private class SystemPythonServiceImpl : SystemPythonService, SimplePersistentStateComponent<MyServiceState>(MyServiceState()) {

  override suspend fun registerSystemPython(pythonPath: PythonBinary): Result<SystemPython, @Nls String> {
    val impl = PythonWithLanguageLevelImpl.createByPythonBinary(pythonPath).getOr { return it }
    state.userProvidedPythons.add(pythonPath)
    return Result.success(SystemPython(impl))
  }

  override fun getInstaller(eelApi: EelApi): PythonInstallerService? =
    if (eelApi == localEel) LocalPythonInstaller else null

  override suspend fun findSystemPythons(eelApi: EelApi): Set<SystemPython> = withContext(Dispatchers.IO) {
    val corePythons = if (eelApi == localEel)
      PythonSdkFlavor.getApplicableFlavors(false)
        .flatMap {
          it.dropCaches()
          it.suggestLocalHomePaths(null, null)
        }
    else emptyList()

    val pythonsFromExtensions = SystemPythonProvider.EP
      .extensionList
      .flatMap { it.findSystemPythons(eelApi) }.filter { it.getEelDescriptor().upgrade() == eelApi }

    val badPythons = mutableSetOf<PythonBinary>()
    val pythons = corePythons + pythonsFromExtensions + state.userProvidedPythons.filter { it.getEelDescriptor() == eelApi.descriptor }

    val result = pythons.toSet()
      .mapNotNull { python ->
        when (val r = PythonWithLanguageLevelImpl.createByPythonBinary(python)) {
          is Result.Success -> SystemPython(r.result)
          is Result.Failure -> {
            fileLogger().info("Skipping $python : ${r.error}")
            badPythons.add(python)
            null
          }
        }

      }.toSet()
    // Remove stale pythons from cache
    state.userProvidedPythons.removeAll(badPythons)
    return@withContext result
  }


  class MyServiceState : BaseState() {
    val userProvidedPythons: MutableCollection<PythonBinary> by list()
  }
}


private object LocalPythonInstaller : PythonInstallerService {
  override suspend fun installLatestPython(): Result<Unit, String> {
    val pythonToInstall =
      withContext(Dispatchers.IO) {
        PySdkToInstallManager.getAvailableVersionsToInstall().toSortedMap().values.last()
      }
    withContext(Dispatchers.EDT) {
      installBinary(pythonToInstall, null) {
      }
    }.getOrElse {
      return Result.failure(it.message ?: it.toString())
    }
    return Result.success(Unit)
  }
}