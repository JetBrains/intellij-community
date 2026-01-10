// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.net.HttpURLConnection
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.io.path.*

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
object PyEnvDownloadCache {
  private val LOG = Logger.getInstance(PyEnvDownloadCache::class.java)

  private val cacheDir: Path by lazy {
    val userHome = System.getProperty("user.home")
    val baseDir = Path.of(userHome, ".cache", "intellij-python-test-env", "downloads")
    baseDir.createDirectories()
    LOG.info("Download cache directory: $baseDir")
    baseDir
  }

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
  suspend fun getOrDownload(url: String, filename: String): Path {
    val cacheKey = generateCacheKey(url)
    val cachedFile = cacheDir.resolve("${cacheKey}_${filename}")
    val lockFile = cacheDir.resolve("${cacheKey}_${filename}.lock")

    // Fast path: check if already cached
    if (cachedFile.exists() && cachedFile.fileSize() > 0) {
      LOG.info("Using cached file: $cachedFile (from $url)")
      return cachedFile
    }

    // Use file-based lock for this specific download
    val lockChannel = FileChannel.open(
      lockFile,
      StandardOpenOption.CREATE,
      StandardOpenOption.WRITE
    )

    try {
      val fileLock = lockChannel.lock()
      try {
        // Double-check after acquiring lock
        if (cachedFile.exists() && cachedFile.fileSize() > 0) {
          LOG.info("Using cached file (double-check): $cachedFile")
          return cachedFile
        }

        LOG.info("Downloading $url to cache: $cachedFile")
        return downloadFile(url, cachedFile)
      }
      finally {
        fileLock.release()
      }
    }
    finally {
      lockChannel.close()
      lockFile.deleteIfExists()
    }
  }

  /**
   * Download a file from URL to the specified path.
   */
  private suspend fun downloadFile(url: String, destination: Path): Path = withContext(Dispatchers.IO) {
    val tempFile = Files.createTempFile(cacheDir, "download_", ".tmp")

    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 30000
    connection.readTimeout = 30000

    if (connection.responseCode != 200) {
      tempFile.deleteIfExists()
      error("Failed to download from $url: HTTP ${connection.responseCode}")
    }

    connection.inputStream.use { input ->
      Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
    }

    Files.move(tempFile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)

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

  /**
   * Get the base cache directory path (parent of downloads directory).
   * Use this for storing unpacked distributives alongside downloads.
   */
  fun cacheDirectory(): Path = cacheDir.parent

}
