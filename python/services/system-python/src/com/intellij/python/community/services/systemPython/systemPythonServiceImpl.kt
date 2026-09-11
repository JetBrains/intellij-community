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
import com.intellij.openapi.diagnostic.debug
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
import com.intellij.python.community.services.systemPython.impl.BinaryStamp
import com.intellij.python.community.services.systemPython.impl.asSysPythonRegisterError
import com.intellij.python.community.services.systemPython.impl.binaryStamp
import com.intellij.python.community.services.systemPython.impl.isSystemPython
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.intellij.python.sdk.backend.getPythonInfo
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
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
import java.util.concurrent.ConcurrentHashMap
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

  /**
   * What each python binary reported about itself, by the path of the binary.
   * It keeps a repeated search from starting an interpreter that did not change.
   */
  private val pythonInfoCache = ConcurrentHashMap<PythonBinary, ReadPythonInfo>()

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
    val brokenPython = PySystemPythonBundle.message("py.system.python.service.python.is.broken", pythonPath)
    val environment = pythonPath.detectPythonEnvironment()
      .getOr(brokenPython) { return Result.failure(it.error.asSysPythonRegisterError()) }
    val pythonInfo = environment.getPythonInfo()
      .getOr(brokenPython) { return Result.failure(it.error.asSysPythonRegisterError()) }
    val pythonWithLangLevel = VanillaPythonWithPythonInfoImpl.create(pythonPath, pythonInfo)
    if (!environment.isSystemPython) {
      return Result.failure(SysPythonRegisterError.NotASystemPython(pythonWithLangLevel))
    }
    val systemPython = SystemPython.create(pythonWithLangLevel, null)

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

      val pythons = (pythonsFromExtensions + state.userProvidedPythonsAsPath.filter { it.getEelDescriptor() == eelApi.descriptor }).toSet()
      val badPythons = mutableSetOf<PythonBinary>()
      val result = VanillaPythonWithPythonInfoImpl.mapConcurrently(pythons) { python -> systemPythonOrNull(python, pythonsUi[python]) }
        .mapNotNull { (python, r) ->
          when (r) {
            is Result.Success -> r.result ?: run {
              logger.debug { "Skipping $python : it is not a system python" }
              badPythons.add(python)
              null
            }
            is Result.Failure -> {
              logger.warn("Skipping $python : ${r.error}")
              badPythons.add(python)
              null
            }
          }
        }.toSet()
      pythonInfoCache.keys.removeAll { it !in pythons && it.getEelDescriptor() == eelApi.descriptor }

      // Remove stale pythons from the cache
      val newPaths = state.userProvidedPythons.distinct().toMutableList()
      newPaths.removeAll(badPythons.map { it.pathString })
      state.userProvidedPythons.clear()
      state.userProvidedPythons.addAll(newPaths)
      logger.info("pythons refreshed")
      return@withContext result.sorted()
    }
  }

  /**
   * The [SystemPython] for [python], `null` when [python] is not a system python, or an error when it is broken.
   *
   * The file system layout tells a system python from any other environment, so a virtual environment and a conda
   * environment never start here. A system python records no version, so only it starts, and only when the binary
   * changed since the last search. See PY-88315.
   */
  private suspend fun systemPythonOrNull(python: PythonBinary, ui: PyToolUIInfo?): PyResult<SystemPython?> {
    val environment = python.detectPythonEnvironment().getOr { return it }
    if (!environment.isSystemPython) {
      pythonInfoCache.remove(python)
      return Result.success(null)
    }

    val stamp = python.binaryStamp()
    val readEarlier = pythonInfoCache[python]?.takeIf { it.stamp == stamp }
    val pythonInfo = readEarlier?.pythonInfo ?: environment.getPythonInfo().getOr { return it }
    if (readEarlier == null && stamp != null) {
      pythonInfoCache[python] = ReadPythonInfo(stamp, pythonInfo)
    }
    return Result.success(SystemPython.create(VanillaPythonWithPythonInfoImpl.create(python, pythonInfo), ui))
  }
}


/**
 * What a python binary reported about itself, and the state of the file that reported it.
 *
 * Only the interpreter itself knows this, and it changes only when the binary changes, so a later search reuses the
 * record. An environment can run a startup hook on each start of the interpreter, and such a hook can be expensive.
 */
private class ReadPythonInfo(val stamp: BinaryStamp, val pythonInfo: PythonInfo)

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