// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.*
import com.intellij.openapi.components.Service.Level.APP
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.impl.installer.PySdkToInstallManager
import com.intellij.python.community.services.internal.impl.VanillaPythonWithLanguageLevelImpl
import com.intellij.python.community.services.shared.UICustomization
import com.intellij.python.community.services.systemPython.SystemPythonServiceImpl.MyServiceState
import com.intellij.python.community.services.systemPython.impl.Cache
import com.intellij.python.community.services.systemPython.impl.PySystemPythonBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.installer.installBinary
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


private val logger = fileLogger()

// null means "disabled"
internal suspend fun getCacheTimeout(): Duration? =
  // This function is suspending because registry might not be available before the application is fully loaded.
  RegistryManager.getInstanceAsync().get("python.system.refresh.minutes").asInteger().let { i ->
    if (i > 0) i.minutes else null
  }


@Service(APP)
@State(name = "SystemPythonService", storages = [Storage("systemPythonService.xml", roamingType = RoamingType.LOCAL)],
       allowLoadInTests = true)
@Internal
internal class SystemPythonServiceImpl(scope: CoroutineScope) : SystemPythonService, SimplePersistentStateComponent<MyServiceState>(MyServiceState()) {
  private val findPythonsMutex = Mutex()
  private val _cacheImpl: CompletableDeferred<Cache<EelDescriptor, SystemPython>?> = CompletableDeferred()
  private suspend fun cache() = _cacheImpl.await()

  init {
    scope.launch {
      _cacheImpl.complete(getCacheTimeout()?.let { interval ->
        Cache<EelDescriptor, SystemPython>(scope, interval) { eelDescriptor ->
          searchPythonsPhysicallyNoCache(eelDescriptor.toEelApi())
        }
      })
    }
  }

  override suspend fun registerSystemPython(pythonPath: PythonBinary): PyResult<SystemPython> {
    val pythonWithLangLevel = VanillaPythonWithLanguageLevelImpl.createByPythonBinary(pythonPath)
      .getOr(PySystemPythonBundle.message("py.system.python.service.python.is.broken", pythonPath)) { return it }
    val systemPython = SystemPython(pythonWithLangLevel, null)
    state.userProvidedPythons.add(pythonPath.pathString)
    cache()?.get(pythonPath.getEelDescriptor())?.add(systemPython)
    return Result.success(systemPython)
  }

  override fun getInstaller(eelApi: EelApi): PythonInstallerService? =
    if (eelApi == localEel) LocalPythonInstaller else null

  override suspend fun findSystemPythons(eelApi: EelApi, forceRefresh: Boolean): List<SystemPython> =
    cache()?.let { cache ->
      // Cache enabled
      cache.startUpdate()
      if (forceRefresh) {
        logger.info("pythons refresh requested")
        cache.updateCache(eelApi.descriptor) // Update cache and suspend till update finished
      }
      else {
        cache.get(eelApi.descriptor)
      }.sorted()
    } ?: searchPythonsPhysicallyNoCache(eelApi).sorted()


  class MyServiceState : BaseState() {
    // Only strings are supported by serializer
    var userProvidedPythons by list<String>()
    val userProvidedPythonsAsPath: Collection<Path>
      get() = userProvidedPythons.mapNotNull {
        try {
          Path.of(it)
        }
        catch (_: InvalidPathException) {
          logger.warn("invalid path $it")
          null
        }
      }
  }


  private suspend fun searchPythonsPhysicallyNoCache(eelApi: EelApi): List<SystemPython> = withContext(Dispatchers.IO) {
    findPythonsMutex.withLock {
      val pythonsUi = mutableMapOf<PythonBinary, UICustomization>()

      val pythonsFromExtensions = SystemPythonProvider.EP.extensionList
        .flatMap { provider ->
          val pythons = provider.findSystemPythons(eelApi).getOrNull() ?: emptyList()
          val ui = provider.uiCustomization
          if (ui != null) {
            pythons.forEach { pythonsUi[it] = ui }
          }
          pythons
        }

      val badPythons = mutableSetOf<PythonBinary>()
      val pythons = pythonsFromExtensions + state.userProvidedPythonsAsPath.filter { it.getEelDescriptor() == eelApi.descriptor }

      val result = VanillaPythonWithLanguageLevelImpl.createByPythonBinaries(pythons.toSet())
        .mapNotNull { (python, r) ->
          when (r) {
            is Result.Success -> SystemPython(r.result, pythonsUi[r.result.pythonBinary])
            is Result.Failure -> {
              fileLogger().warn("Skipping $python : ${r.error}")
              badPythons.add(python)
              null
            }
          }

        }.toSet()
      // Remove stale pythons from the cache
      state.userProvidedPythons.removeAll(badPythons.map { it.pathString })
      logger.info("pythons refreshed")
      return@withContext result.sorted()
    }
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
      return Result.Companion.failure(it.message ?: it.toString())
    }
    return Result.Companion.success(Unit)
  }
}