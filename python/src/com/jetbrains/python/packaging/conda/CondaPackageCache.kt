// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.cache.MappedPackedAsciiStringSetCacheFile
import com.jetbrains.python.packaging.cache.PythonPackageCache
import com.jetbrains.python.packaging.cache.PythonPackageCacheIOError
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import kotlin.time.measureTimedValue

@ApiStatus.Internal
@Service
class CondaPackageCache : PythonPackageCache {
  private val cache =
    MappedPackedAsciiStringSetCacheFile(CONDA_CACHE_FILE_PATH) {
      val timedValue = measureTimedValue {
        val versionInfoResults = coroutineScope {
          val versionInfoFutures = mutableListOf<Deferred<Result<PackageVersionMap, CondaPackageInfoFetcher.FetcherError>>>()

          for (channel in CondaChannel.entries) {
            val currentArch = CondaArch.getCurrentArch()

            versionInfoFutures += async { CondaPackageInfoFetcher.fetchCondaChannelPackages(channel, currentArch) }

            if (currentArch != CondaArch.NOARCH) {
              versionInfoFutures += async { CondaPackageInfoFetcher.fetchCondaChannelPackages(channel, CondaArch.NOARCH) }
            }
          }

          versionInfoFutures.awaitAll()
        }

        val errorMessages = mutableListOf<String>()
        val versionInfos = mutableListOf<PackageVersionMap>()

        loop@ for (infoResult in versionInfoResults) {
          versionInfos += infoResult.getOr { error ->
            errorMessages += error.error.message
            continue@loop
          }
        }

        if (errorMessages.isNotEmpty()) {
          return@MappedPackedAsciiStringSetCacheFile Result.Failure(
            PythonPackageCacheIOError.FailedToFetchPackages(errorMessages.joinToString("\n"))
          )
        }

        Result.Success(
          versionInfos
            .asSequence()
            .flatMap { it.asSequence() }
            .groupingBy { it.key }
            .aggregate { _, accumulator: Set<String>?, element, _ ->
              if (accumulator == null) {
                element.value
              }
              else {
                accumulator + element.value
              }
            }
        )
      }
      
      logger.info("conda package refetched reloaded in ${timedValue.duration}")
      
      timedValue.value
    }

  override val size: Int
    get() =
      cache.size

  override suspend fun contains(name: String): Boolean =
    withContext(Dispatchers.IO) {
      name in cache
    }

  override suspend fun search(prefix: String, pageSize: Int): PythonPackageSearchResult =
    withContext(Dispatchers.IO) {
      cache.search(prefix.lowercase(), pageSize)
    }

  @CheckReturnValue
  override suspend fun reload(): Result<Unit, PythonPackageCacheIOError> =
    cache.reload()

  @CheckReturnValue
  override suspend fun fullRefetchAndReload(): Result<Unit, PythonPackageCacheIOError> =
    cache.reload(forceRefetch = true)

  suspend fun getVersionsOfPackage(packageName: String): Set<String>? =
    withContext(Dispatchers.IO) {
      cache[packageName]
    }

  companion object {
    private const val CACHE_FOLDER = "python_packages"
    private val CONDA_CACHE_FILE_PATH =
      PathManager.getSystemDir().resolve(CACHE_FOLDER, "conda.txt")
    private val logger = thisLogger()
  }
}