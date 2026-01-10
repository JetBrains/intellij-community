// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.plain

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.python.test.env.core.*
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.system.OS
import com.jetbrains.python.PythonBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.net.URLDecoder.decode
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/**
 * Environment provider for plain Python (no venv).
 * 
 * This provider:
 * 1. Downloads Python from python-build-standalone for the current OS
 * 2. If no libraries needed: Extracts to ~/.cache/python/<version>/ and returns cached Python
 * 3. If libraries needed: Extracts to plain_x/ and installs libraries there
 * 
 * Downloads Python from: https://github.com/astral-sh/python-build-standalone/releases
 * 
 */
@ApiStatus.Internal
class PlainPyEnvironmentProvider : PyEnvironmentProvider<PlainPyEnvironmentSpec>("plain") {

  override suspend fun setupEnvironment(context: Context, spec: PlainPyEnvironmentSpec): PyEnvironment {
    val logger = thisLogger()
    logger.info("Setting up plain Python environment")
    logger.info("Python version: ${spec.pythonVersion}")
    
    val archive = downloadPython(spec.pythonVersion.toString())
    
    if (spec.libraries.isEmpty()) {
      logger.info("No libraries specified, using cached Python")
      val cachedPython = setupPython(archive, getCacheDir(archive))
      logger.info("Plain Python path: $cachedPython")
      logger.info("Plain Python environment setup completed")
      return PlainPyEnvironment(cachedPython, cachedPython.parent.parent, false)
    } else {
      val targetPath = nextEnvPath(context.workingDir)
      logger.info("Setting up plain Python with libraries at: $targetPath")
      val python = setupPython(archive, targetPath)
      logger.info("Plain Python path: $python")
      logger.info("Installing libraries: ${spec.libraries.joinToString(", ")}")
      installLibraries(python, spec.libraries)
      logger.info("Libraries installed successfully")
      logger.info("Plain Python environment setup completed at: $targetPath")
      return PlainPyEnvironment(python, targetPath, true)
    }
  }
  
  private suspend fun downloadPython(pythonVersion: String): Path {
    val logger = thisLogger()
    val downloadInfo = PyVersionMapping.getDownloadInfo(pythonVersion)
    val archiveFileName = downloadInfo.url.substringAfterLast('/')
    
    val decodedFileName = decode(archiveFileName, "UTF-8")
    val sanitizedFileName = sanitizeFileName(decodedFileName, replacement = "_", truncateIfNeeded = false)
    
    logger.info("Downloading Python archive: $sanitizedFileName (from URL: $archiveFileName)")
    val archive = PyEnvDownloadCache.getOrDownload(downloadInfo.url, sanitizedFileName)
    logger.info("Python archive downloaded: $archive")
    
    // Verify size and SHA256
    logger.info("Verifying download integrity")
    verifyDownload(archive, downloadInfo.size, downloadInfo.sha256)
    logger.info("Download verification successful")
    
    return archive
  }
  
  private suspend fun verifyDownload(file: Path, expectedSize: Long, expectedSha256: String) = withContext(Dispatchers.IO) {
    // Check size first (faster)
    val actualSize = file.fileSize()
    if (actualSize != expectedSize) {
      error("File size mismatch for $file. Expected: $expectedSize bytes, Actual: $actualSize bytes")
    }
    
    // Then verify SHA256
    val actualSha256 = com.intellij.util.io.sha256Hex(file)
    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
      error("SHA256 checksum mismatch for $file. Expected: $expectedSha256, Actual: $actualSha256")
    }
  }
  
  private fun getCacheDir(archive: Path): Path {
    val fileName = archive.fileName.toString()
    val archiveBaseName = when {
      fileName.endsWith(".tar.gz", ignoreCase = true) -> fileName.substringBeforeLast(".tar.gz")
      fileName.endsWith(".zip", ignoreCase = true) -> fileName.substringBeforeLast(".zip")
      else -> fileName
    }
    return PyEnvDownloadCache.cacheDirectory().resolve("python").resolve(archiveBaseName)
  }

  private suspend fun installLibraries(pythonPath: Path, libraries: List<String>) {
    installPipPackages(pythonPath, libraries, thisLogger())
  }

  /**
   * Extracts Python archive to the specified directory.
   */
  private suspend fun setupPython(archive: Path, targetDir: Path): Path {
    val logger = thisLogger()
    
    extractIfNecessary(targetDir, logger) { target ->
      unpackArchive(archive, target)
      val pythonPath = findPythonExecutableInInstall(target)
      markExecutable(logger, pythonPath)
    }
    
    return findPythonExecutableInInstall(targetDir)
  }

  /**
   * Find Python executable in the extracted installation.
   */
  private fun findPythonExecutableInInstall(pythonDir: Path): Path {
    if (!pythonDir.exists()) {
      error("Python directory does not exist: $pythonDir")
    }
    
    val possiblePaths = if (OS.CURRENT == OS.Windows) {
      listOf(
        pythonDir.resolve("python.exe"),
        pythonDir.resolve("Scripts").resolve("python.exe"),
        pythonDir.resolve("bin").resolve("python.exe")
      )
    } else {
      listOf(
        pythonDir.resolve("bin").resolve("python3"),
        pythonDir.resolve("bin").resolve("python"),
        pythonDir.resolve("python")
      )
    }

    return possiblePaths.firstOrNull { it.exists() }
      ?: error("Python executable not found in $pythonDir. Tried: ${possiblePaths.joinToString(", ")}")
  }

  /**
   * Plain Python environment implementation
   */
  private class PlainPyEnvironment(
    override val pythonPath: PythonBinary,
    override val envPath: Path,
    private val cleanUpOnClose: Boolean,
  ) : PyEnvironment {
    @OptIn(ExperimentalPathApi::class)
    override fun close() {
      if (cleanUpOnClose) {
        envPath.deleteRecursively()
      }
    }
  }
}
