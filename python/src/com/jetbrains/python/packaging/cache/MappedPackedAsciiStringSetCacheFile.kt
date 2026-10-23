// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.cache

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.SafeFileOutputStream
import com.jetbrains.python.packaging.pip.PackedAsciiStringSet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.CheckReturnValue
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.cache.impl.MemoryMappedSearchPage
import com.jetbrains.python.packaging.conda.PackageVersionMap
import com.jetbrains.python.packaging.conda.VersionSet
import com.jetbrains.python.packaging.pip.PackedAsciiStringSet.InvalidFormatError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.foreign.Arena
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import kotlin.concurrent.write
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

internal class MappedPackedAsciiStringSetCacheFile<TReloadError>(
  val filePath: Path,
  val cacheValidFor: Duration = Duration.ofDays(1),

  /**
   * A function that fetches package data. This data is then used to populate the cache when it needs to be reloaded. The package data
   * takes a form of a map, in which each key is a package name and value is a set of package version strings.
   */
  val dataFetcher: suspend () -> Result<PackageVersionMap, TReloadError>,
) {
  private val reloadLock = Mutex()
  private val cacheReadWriteLock = ReentrantReadWriteLock()
  private var arena = Arena.ofShared()
  private var reloadInProgress: Boolean = false
  private var set = PackedAsciiStringSet.create(ByteBuffer.allocate(0)).orThrow()
  private var setSize = 0
  private val cacheName = filePath.nameWithoutExtension
  private val tempFilePath =
    run {
      val extension = filePath.extension.takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""
      filePath.resolveSibling("${cacheName}_tmp$extension")
    }

  val size: Int
    get() =
      setSize

  @RequiresBackgroundThread
  operator fun contains(packageName: String): Boolean =
    cacheReadWriteLock.read {
      packageName in set
    }

  /**
   * Tries to perform the contains operation. Doesn't block when cache is being reloaded, instead returning null. Safe to execute on EDT.
   * 
   * @return contains result, or null if the cache is currently being reloaded.
   */
  fun tryContains(packageName: String): Boolean? {
    val readLock = cacheReadWriteLock.readLock()

    if (!readLock.tryLock()) {
      return null
    }

    try {
      return packageName in set
    } finally {
      readLock.unlock()
    }
  }

  @RequiresBackgroundThread
  fun search(prefix: String, pageSize: Int): PythonPackageSearchResult =
    cacheReadWriteLock.read {
      val result = set.searchByPrefix(prefix, pageSize)

      PythonPackageSearchResult(
        result.total,
        result.pages.map { page ->
          MemoryMappedSearchPage(page)
        },
        pageSize,
      )
    }

  @RequiresBackgroundThread
  operator fun get(name: String): VersionSet? =
    cacheReadWriteLock.read {
      set[name]?.splitToSequence(',')?.map { it.trim() }?.toSet()
    }

  @RequiresBackgroundThread
  fun asList(): List<String> =
    set.asSequence().map { it.name }.toList()

  @CheckReturnValue
  suspend fun reload(forceRefetch: Boolean = false): Result<Unit, TReloadError> {
    reloadLock.withLock {
      if ((set.size > 0 && !forceRefetch) || reloadInProgress) {
        return Result.success(Unit)
      }

      reloadInProgress = true
    }

    try {
      withContext(Dispatchers.IO) {
        if (forceRefetch || !tryLoadFromFile()) {
          val newCache = dataFetcher().getOr {
            logger.warn("$cacheName: failed to reload cache")

            return@withContext it
          }

          val new =
            newCache
              .entries
              .sortedBy { it.key }
              .map {
                buildString {
                  append(it.key)

                  if (it.value.isEmpty()) {
                    return@buildString
                  }

                  append(':')

                  for ((index, version) in it.value.withIndex()) {
                    append(version)

                    if (index != it.value.size - 1) {
                      append(",")
                    }
                  }
                }
              }

          remap(new).orThrow() // cannot fail: recreates the cache file and instantly maps it 
          logger.debug { "$cacheName: reloaded cache" }
        }

        Result.Success(Unit)
      }.getOr { return it }

      if (set.size == 0) {
        logger.warn("$cacheName: loaded empty cache")
      }
    }
    finally {
      reloadLock.withLock {
        reloadInProgress = false
      }
    }

    logger.debug { "$cacheName: loaded cache" }

    return Result.success(Unit)
  }

  @RequiresBackgroundThread
  private suspend fun tryLoadFromFile(): Boolean {
    return withContext(Dispatchers.IO) {
      if (isFileCacheExpired()) {
        logger.debug { "$cacheName: cache file expired" }
        return@withContext false
      }

      remap().getOr { error ->
        logger.debug { "$cacheName: cache file corrupted: ${error.error.message}" }
        return@withContext false
      }

      logger.debug {
        "$cacheName: package list loaded to ${filePath} with ${Files.size(filePath) / 1024}Kb in size and ${set.size} cache entries"
      }

      true
    }
  }

  @RequiresBackgroundThread
  private fun isFileCacheExpired(): Boolean {
    if (!filePath.exists()) {
      return true
    }

    val fileTime = Files.getLastModifiedTime(filePath)
    val expirationTime = fileTime.toInstant().plus(cacheValidFor)

    return expirationTime.isBefore(Instant.now())
  }

  @RequiresBackgroundThread
  @CheckReturnValue
  private fun remap(storeBeforeLoading: List<String>? = null): Result<Unit, InvalidFormatError> {
    var fileToMap = filePath

    storeBeforeLoading?.also { packages ->
      Files.createDirectories(filePath.parent)
      fileToMap = tempFilePath
      SafeFileOutputStream(fileToMap).writer(StandardCharsets.US_ASCII).use { writer ->
        for (pkg in packages) {
          writer.write(pkg)
          writer.write('\n'.code)
        }
      }
    }

    cacheReadWriteLock.write {
      set = PackedAsciiStringSet.create(ByteBuffer.allocate(0)).orThrow()
      setSize = 0
      arena.close()

      FileChannel.open(fileToMap, StandardOpenOption.READ).use { channel ->
        arena = Arena.ofShared()
        set =
          if (Registry.`is`("disable.python.cache.memory.mapping")) {
            val buffer = ByteBuffer.allocate(channel.size().toInt())
            channel.read(buffer)

            PackedAsciiStringSet.create(buffer)
          }
          else {
            PackedAsciiStringSet.create(
              channel
                .map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena)
                .asByteBuffer()
            )
          }.getOr {
            return it
          }
        setSize = set.size
      }
    }

    storeBeforeLoading?.also {
      Files.deleteIfExists(filePath)
      Files.move(fileToMap, filePath)
    }

    return Result.Success(Unit)
  }

  companion object {
    private val logger = thisLogger()
  }
}