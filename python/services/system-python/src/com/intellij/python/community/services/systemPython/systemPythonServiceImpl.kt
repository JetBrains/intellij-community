// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.*
import com.intellij.openapi.components.Service.Level.APP
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.impl.installer.PySdkToInstallManager
import com.intellij.python.community.services.internal.impl.PythonWithLanguageLevelImpl
import com.intellij.python.community.services.systemPython.SystemPythonServiceImpl.MyServiceState
import com.intellij.python.community.services.systemPython.impl.Cache
import com.intellij.python.community.services.systemPython.impl.CoreSystemPythonProvider
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.installer.installBinary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


private val logger = fileLogger()

// null means "disabled"
internal val cacheTimeout: Duration?
  get() = Registry.get("python.system.refresh.minutes").asInteger().let { i ->
    if (i > 0) i.minutes else null
  }


@Service(APP)
@State(name = "SystemPythonService", storages = [Storage("systemPythonService.xml", roamingType = RoamingType.LOCAL)],
       allowLoadInTests = true)
@Internal
internal class SystemPythonServiceImpl(scope: CoroutineScope) : SystemPythonService, SimplePersistentStateComponent<MyServiceState>(MyServiceState()) {
  private val findPythonsMutex = Mutex()
  private val cache: Cache<EelDescriptor, SystemPython>? = cacheTimeout?.let { interval ->
    Cache(scope, interval) { eelDescriptor ->
      searchPythonsPhysicallyNoCache(eelDescriptor.upgrade())
    }
  }


  override suspend fun registerSystemPython(pythonPath: PythonBinary): Result<SystemPython, @Nls String> {
    val pythonWithLangLevel = PythonWithLanguageLevelImpl.createByPythonBinary(pythonPath).getOr { return it }
    val systemPython = SystemPython(pythonWithLangLevel, null)
    state.userProvidedPythons.add(pythonPath.pathString)
    cache?.get(pythonPath.getEelDescriptor())?.add(systemPython)
    return Result.success(systemPython)
  }

  override fun getInstaller(eelApi: EelApi): PythonInstallerService? =
    if (eelApi == localEel) LocalPythonInstaller else null

  override suspend fun findSystemPythons(eelApi: EelApi, forceRefresh: Boolean): List<SystemPython> =
    if (cache != null) {
      // Cache enabled
      cache.startUpdate()
      if (forceRefresh) {
        logger.info("pythons refresh requested")
        cache.updateCache(eelApi.descriptor) // Update cache and suspend till update finished
      }
      else {
        cache.get(eelApi.descriptor)
      }.sorted()
    }
    else {
      // Cache disabled
      searchPythonsPhysicallyNoCache(eelApi)
    }

  class MyServiceState : BaseState() {
    // Only strings are supported by serializer
    var userProvidedPythons by list<String>()
    val userProvidedPythonsAsPath: Collection<Path>
      get() = userProvidedPythons.filterNotNull().mapNotNull {
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

      val pythonsFromExtensions = (SystemPythonProvider.EP
                                     .extensionList + listOf(CoreSystemPythonProvider))
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

      val result = PythonWithLanguageLevelImpl.createByPythonBinaries(pythons.toSet())
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