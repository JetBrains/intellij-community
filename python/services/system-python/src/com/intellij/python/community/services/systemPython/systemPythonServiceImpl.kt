// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.APP
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.community.impl.installer.PySdkToInstallManager
import com.intellij.python.community.services.internal.impl.VanillaPythonWithPythonInfoImpl
import com.intellij.python.community.services.systemPython.SystemPythonServiceImpl.MyServiceState
import com.intellij.python.community.services.systemPython.impl.Cache
import com.intellij.python.community.services.systemPython.impl.EelDescriptorFilter.Companion.isEphemeral
import com.intellij.python.community.services.systemPython.impl.PySystemPythonBundle
import com.intellij.python.community.services.systemPython.impl.UpdateCacheDelayer
import com.intellij.python.community.services.systemPython.impl.asSysPythonRegisterError
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.jetbrains.python.sdk.installer.installBinary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
class SystemPythonServiceImpl internal constructor(
  scope: CoroutineScope,
  createUpdateCacheDelayer: suspend () -> UpdateCacheDelayer?,
) : SystemPythonService,
    SimplePersistentStateComponent<MyServiceState>(MyServiceState()) {
  constructor(scope: CoroutineScope) : this(scope, {
    val duration = getCacheTimeout()
    if (duration != null) UpdateCacheDelayer.TimeBased(duration) else null
  })

  private val findPythonsMutex = Mutex()
  private val _cacheImpl: CompletableDeferred<Cache<EelDescriptor, SystemPython>?> = CompletableDeferred()
  private suspend fun cache() = _cacheImpl.await()

  init {
    scope.launch {
      _cacheImpl.complete(createUpdateCacheDelayer()?.let { delayer ->
        Cache(scope, delayer) { eelDescriptor ->
          withContext(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
            searchPythonsPhysicallyNoCache(eelDescriptor.toEelApi())
          }
        }
      })
    }
  }

  override suspend fun registerSystemPython(pythonPath: PythonBinary): Result<SystemPython, SysPythonRegisterError> {
    val pythonWithLangLevel = VanillaPythonWithPythonInfoImpl.createByPythonBinary(pythonPath)
      .getOr(PySystemPythonBundle.message("py.system.python.service.python.is.broken",
                                          pythonPath)) { return Result.failure(it.error.asSysPythonRegisterError()) }
    val systemPython = SystemPython.create(pythonWithLangLevel, null).getOr { return it }

    val eelDescriptor = pythonPath.getEelDescriptor()
    if (!eelDescriptor.isEphemeral) {
      state.userProvidedPythons.add(pythonPath.pathString)
      logger.debug("Registering $pythonPath")
      cache()?.get(eelDescriptor)?.add(systemPython)
    }
    return Result.success(systemPython)
  }

  override fun getInstaller(eelApi: EelApi): PythonInstallerService? =
    if (eelApi == localEel) LocalPythonInstaller else null

  override suspend fun findSystemPythons(eelApi: EelApi, forceRefresh: Boolean): List<SystemPython> {
    val eelDescriptor = eelApi.descriptor
    val cache = if (!eelDescriptor.isEphemeral) cache() else null
    return cache?.let { cache ->
      // Cache enabled
      cache.startUpdate()
      if (forceRefresh) {
        logger.info("pythons refresh requested")
        cache.updateCache(eelDescriptor) // Update cache and suspend till update finished
      }
      else {
        cache.get(eelDescriptor)
      }.sortedSystemPythons()
    } ?: searchPythonsPhysicallyNoCache(eelApi).sortedSystemPythons()
  }

  private fun Iterable<SystemPython>.sortedSystemPythons(): List<SystemPython> =
    sortedWith(
      // Free-threaded Python is unstable, we don't want to have it selected by default if we have alternatives
      compareBy<SystemPython> { it.pythonInfo.freeThreaded }.thenByDescending { it.pythonInfo.languageLevel }
    )


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
      val pythonsUi = mutableMapOf<PythonBinary, PyToolUIInfo>()

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

      val result = VanillaPythonWithPythonInfoImpl.createByPythonBinaries(pythons.toSet())
        .mapNotNull { (python, r) ->
          val sysPython = r.mapSuccessError(
            onSuccess = { r -> SystemPython.create(r, pythonsUi[r.pythonBinary]) },
            onErr = { it.asSysPythonRegisterError() }
          )
          when (sysPython) {
            is Result.Success -> sysPython.result
            is Result.Failure -> {
              fileLogger().warn("Skipping $python : ${sysPython.error.asPyError}")
              badPythons.add(python)
              null
            }
          }

        }.toSet()
      // Remove stale pythons from the cache
      val newPaths = state.userProvidedPythons.distinct().toMutableList()
      newPaths.removeAll(badPythons.map { it.pathString })
      state.userProvidedPythons.clear()
      state.userProvidedPythons.addAll(newPaths)
      logger.info("pythons refreshed")
      return@withContext result.sorted()
    }
  }
}


private object LocalPythonInstaller : PythonInstallerService {
  override suspend fun installLatestPython(versionSpecifiers: PyVersionSpecifiers): Result<Unit, String> {
    val pythonToInstall = withContext(Dispatchers.IO) {
      PySdkToInstallManager.getAvailableVersionsToInstall()
        .filterKeys { versionSpecifiers.isValid(it) }
        .maxByOrNull { it.key }?.value
    } ?: return Result.Companion.failure("No matching Python version available for installation")
    withContext(Dispatchers.EDT) {
      installBinary(pythonToInstall, null) {
      }
    }.getOrElse {
      return Result.Companion.failure(it.message ?: it.toString())
    }
    return Result.Companion.success(Unit)
  }
}