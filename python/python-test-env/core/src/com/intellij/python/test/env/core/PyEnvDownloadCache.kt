// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize

@ApiStatus.Internal
interface PyEnvHttpConnection : AutoCloseable {
  val responseCode: Int
  val inputStream: InputStream
}

@ApiStatus.Internal
fun interface PyEnvHttpConnectionFactory {
  fun open(url: String): PyEnvHttpConnection
}

/**
 * Shared download cache for Python environment providers.
 * 
 * This cache:
 * - Stores downloaded files persistently across test runs
 * - Prevents redundant downloads of the same resources
 * - Thread-safe for concurrent access
 * - Uses content-based cache keys (URL hash)
 * 
 * Cache directory structure:
 * ```
 * ~/.cache/intellij-python-test-env/
 *   downloads/
 *     <hash1>_filename.ext
 *     <hash2>_filename.ext
 *   lock/
 * ```
 */
@ApiStatus.Internal
class PyEnvDownloadCache(
  private val cacheDir: Path,
  private val connectionFactory: PyEnvHttpConnectionFactory,
) {
  private val downloadLocks = ConcurrentHashMap<String, Mutex>()

  /**
   * Get a cached file or download it if not present.
   * 
   * Uses file-based locking to prevent concurrent downloads of the same resource
   * without blocking other downloads.
   * 
   * @param url URL to download from
   * @param filename Original filename (used for cache file naming)
   * @return Path to the cached file
   */
  suspend fun getOrDownload(url: String, filename: String): Path = getOrDownload(url, filename) { true }

  /**
   * Gets a valid cached file or downloads it twice before reporting a validation failure.
   *
   * The cache lock covers validation, removal, and both download attempts.
   */
  suspend fun getOrDownload(url: String, filename: String, isValid: (Path) -> Boolean): Path = withContext(Dispatchers.IO) {
    val cacheKey = generateCacheKey(url)
    val cachedFile = cacheDir.resolve("${cacheKey}_${filename}")
    val lockFile = cacheDir.resolve("${cacheKey}_${filename}.lock")

    downloadLocks.computeIfAbsent(cacheKey) { Mutex() }.withLock downloadLock@ {
      // Use file-based locking to coordinate separate test processes.
      FileChannel.open(
        lockFile,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE
      ).use { lockChannel ->
        lockChannel.lock().use {
          repeat(2) { attempt ->
            if (cachedFile.exists() && cachedFile.fileSize() > 0 && isValid(cachedFile)) {
              LOG.info("Using cached file: $cachedFile (from $url)")
              return@downloadLock cachedFile
            }

            cachedFile.deleteIfExists()
            val downloadedFile = downloadFile(url, cachedFile)
            if (isValid(downloadedFile)) {
              return@downloadLock downloadedFile
            }

            LOG.warn("Downloaded file did not pass validation: $url (attempt ${attempt + 1})")
          }

          cachedFile.deleteIfExists()
          error("The downloaded file did not pass validation: $url")
        }
      }
    }
  }

  /**
   * Get the base cache directory path (parent of downloads directory).
   * Use this for storing unpacked distributives alongside downloads.
   */
  fun cacheDirectory(): Path = cacheDir.parent

  /**
   * Download a file from URL to the specified path.
   */
  private suspend fun downloadFile(url: String, destination: Path): Path = withContext(Dispatchers.IO) {
    val tempFile = Files.createTempFile(cacheDir, "download_", ".tmp")

    try {
      connectionFactory.open(url).use { connection ->
        val responseCode = connection.responseCode
        if (responseCode != HTTP_OK) {
          error("Failed to download from $url: HTTP $responseCode")
        }

        connection.inputStream.use { input ->
          Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
        }
      }

      Files.move(tempFile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    catch (e: IOException) {
      LOG.error("Couldn't connect to $url", e)
      throw e
    }
    finally {
      tempFile.deleteIfExists()
    }

    LOG.info("Successfully downloaded and cached: $destination")
    destination
  }

  /**
   * Generate a cache key from URL using SHA-256 hash.
   */
  private fun generateCacheKey(url: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(url.toByteArray())
    return hash.joinToString("") { "%02x".format(it) }.take(16)
  }

  private companion object {
    const val HTTP_OK = 200
    val LOG = Logger.getInstance(PyEnvDownloadCache::class.java)
  }
}

val defaultPyEnvDownloadCache: PyEnvDownloadCache by lazy {
  val cacheDir = Path.of(System.getProperty("user.home"), ".cache", "intellij-python-test-env", "downloads")
  cacheDir.createDirectories()
  Logger.getInstance(PyEnvDownloadCache::class.java).info("Download cache directory: $cacheDir")
  PyEnvDownloadCache(cacheDir, PyEnvHttpConnectionFactory(::openHttpConnection))
}

private fun openHttpConnection(url: String): PyEnvHttpConnection {
  val connection = URI(url).toURL().openConnection() as HttpURLConnection
  connection.requestMethod = "GET"
  connection.connectTimeout = 30000
  connection.readTimeout = 30000
  return object : PyEnvHttpConnection {
    override val responseCode: Int
      get() = connection.responseCode
    override val inputStream: InputStream
      get() = connection.inputStream

    override fun close() {
      connection.disconnect()
    }
  }
}
