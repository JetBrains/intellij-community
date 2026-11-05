package com.intellij.python.test.env.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.junit5Tests.framework.winLockedFile.deleteCheckLocking
import com.intellij.util.io.Decompressor
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString

private const val COMPLETION_MARKER_NAME = ".extract_complete"

/**
 * One [Mutex] for each lock file.
 *
 * A [FileChannel] lock belongs to the whole JVM. A second request for the same file from the same
 * JVM throws [java.nio.channels.OverlappingFileLockException], so the file lock alone cannot guard
 * two coroutines.
 */
private val cacheLocks = ConcurrentHashMap<Path, Mutex>()

/**
 * The lock file that guards the cache directory [target].
 *
 * The lock file is a sibling of [target], because the owner of the lock can delete [target].
 */
@ApiStatus.Internal
@TestOnly
@PyInternalExecApi
fun cacheLockFile(target: Path): Path {
  val absoluteTarget = target.toAbsolutePath().normalize()
  return absoluteTarget.resolveSibling("${absoluteTarget.fileName}.lock")
}

/**
 * Runs [action] with exclusive access to [lockFile]. The lock covers every coroutine and every
 * process.
 *
 * The test runner starts one JVM for each test target, and those JVMs share the cache under
 * [PyEnvDownloadCache.cacheDirectory]. Some of the tools that write there are not safe for a
 * concurrent writer, so a caller takes this lock first. Use [cacheLockFile] to name the lock file
 * of a cache directory.
 *
 * The lock file stays on disk after the release. A delete would let the next process lock a new
 * file under the same name, and then two processes would hold the lock at the same time.
 */
@ApiStatus.Internal
suspend fun <T> withCacheLock(lockFile: Path, logger: Logger, action: suspend () -> T): T {
  val absoluteLockFile = lockFile.toAbsolutePath().normalize()
  absoluteLockFile.parent?.createDirectories()
  logger.info("Waiting for the cache lock: $absoluteLockFile")
  return cacheLocks.computeIfAbsent(absoluteLockFile) { Mutex() }.withLock {
    FileChannel.open(absoluteLockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
      val fileLock = channel.lock()
      try {
        action()
      }
      finally {
        fileLock.release()
      }
    }
  }
}

/**
 * Extracts archive to target directory if not already extracted (checked via completion marker).
 *
 * This function implements the standard extraction flow:
 * 1. Check if extraction was already completed (marker exists)
 * 2. Clean up incomplete extractions if directory exists without marker
 * 3. Create target directory and call unpack function
 * 4. Create completion marker
 *
 * Steps 2 to 4 hold the [withCacheLock] lock of [target]. Without the lock, the cleanup of one JVM
 * deletes the files that another JVM writes. The winner then publishes the marker over a damaged
 * installation, and every later test on that machine reuses it. See PY-91298.
 *
 * @param target Target directory for extraction
 * @param logger Logger for operation messages
 * @param unpack Function that performs the actual unpacking to the target directory
 */
@ApiStatus.Internal
@OptIn(ExperimentalPathApi::class, PyInternalExecApi::class)
suspend fun extractIfNecessary(
  target: Path,
  logger: Logger,
  unpack: suspend (Path) -> Unit,
): Unit = withContext(Dispatchers.IO) {
  val completionMarker = target.resolve(COMPLETION_MARKER_NAME)

  // Check if already extracted
  if (completionMarker.exists()) {
    logger.info("Already extracted at: $target")
    return@withContext
  }

  withCacheLock(cacheLockFile(target), logger) {
    if (completionMarker.exists()) {
      // Another process extracted it while this one waited for the lock
      logger.info("Already extracted at: $target")
    }
    else {
      // If directory exists but extraction is incomplete, clean it up
      if (target.exists()) {
        logger.info("Cleaning up incomplete extraction at: $target")
        try {
          target.deleteRecursively()
        }
        catch (e: IOException) {
          logger.warn("Failed to fully delete directory, will retry extraction", e)
        }
      }

      // Extract archive
      logger.info("Extracting to: $target")
      unpack(target)
      logger.info("Extraction completed")

      // Mark extraction as complete
      Files.createFile(completionMarker)
      logger.info("Extraction setup completed")
    }
  }
}

/**
 * Runs [action] on the extraction at [target]. If [action] fails, this function extracts [target] again and
 * tries again.
 *
 * Each attempt first calls [extractIfNecessary] with [unpack]. Then it runs [action] with the [withCacheLock] lock
 * of [target], so no other process uses [target] at the same time. If [action] returns [Result.Failure], the
 * extraction is possibly broken. Then this function deletes it while it still holds the lock, and the next attempt
 * extracts it again. There are [maxAttempts] attempts at most.
 *
 * @return the result of the first successful attempt, or the failure of the last attempt.
 * @throws IOException if this function cannot delete a broken extraction.
 */
@ApiStatus.Internal
@OptIn(PyInternalExecApi::class)
@Throws(IOException::class)
suspend fun <S, E> useExtractionWithRetries(
  target: Path,
  logger: Logger,
  maxAttempts: Int,
  unpack: suspend (Path) -> Unit,
  action: suspend () -> Result<S, E>,
): Result<S, E> {
  require(maxAttempts > 0) { "maxAttempts must be positive: $maxAttempts" }
  var attempt = 1
  while (true) {
    extractIfNecessary(target, logger, unpack)
    val result = withCacheLock(cacheLockFile(target), logger) {
      val result = action()
      when (result) {
        is Result.Success -> Unit
        is Result.Failure -> invalidateExtraction(target, logger)
      }
      result
    }
    when (result) {
      is Result.Success -> return result
      is Result.Failure -> {
        logger.warn("Attempt $attempt of $maxAttempts failed for $target: ${result.error}")
        if (attempt == maxAttempts) return result
        attempt++
      }
    }
  }
}

/**
 * Marks the extraction at [target] as broken, so that the next [extractIfNecessary] extracts it again.
 *
 * The caller must hold the [withCacheLock] lock of [cacheLockFile] of [target]. This function does not take the
 * lock itself, because the lock is not reentrant.
 *
 * This function deletes the completion marker first. Then it tries to delete [target]. If a file of [target] is
 * in use (on Windows, for example), the delete stops, but the marker is already gone. Thus [extractIfNecessary]
 * cleans up and extracts again.
 *
 * @throws IOException if this function cannot delete the completion marker.
 */
@OptIn(ExperimentalPathApi::class)
@Throws(IOException::class)
private suspend fun invalidateExtraction(target: Path, logger: Logger): Unit = withContext(Dispatchers.IO) {
  Files.deleteIfExists(target.resolve(COMPLETION_MARKER_NAME))
  logger.info("Deleting broken extraction at: $target")
  try {
    if (localEel.platform.isWindows) {
      deleteCheckLocking(target)
    }
    target.deleteRecursively()
  }
  catch (e: IOException) {
    logger.warn("Failed to fully delete $target, the next extraction will clean it up", e)
  }
}

/**
 * Installs Python packages using pip.
 *
 * @param pythonPath Path to Python executable
 * @param libraries List of package specifications to install (e.g., "numpy==1.24.0")
 * @param logger The logger to use for output
 * @throws IllegalStateException if pip install fails
 */
@ApiStatus.Internal
suspend fun installPipPackages(
  pythonPath: Path,
  libraries: List<String>,
  logger: Logger
) {
  if (libraries.isEmpty()) {
    logger.info("No libraries to install")
    return
  }

  val command = listOf(
    pythonPath.pathString,
    "-m", "pip", "install",
    "--disable-pip-version-check",
    "--progress-bar", "off"
  ) + libraries

  executeProcess(command, logger, "pip")
}

@OptIn(LowLevelLocalMachineAccess::class)
fun markExecutable(logger: Logger, executable: Path) {
  if (OS.CURRENT != OS.Windows) {
    logger.info("Setting executable permissions")
    Files.setPosixFilePermissions(
      executable,
      setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
      )
    )
  }
}

/**
 * Extract archive using Decompressor framework.
 * Supports tar.gz, .tgz, and .zip formats.
 *
 * @param archiveFile Path to the archive file
 * @param targetDir Directory where archive contents will be extracted
 * @param prefixToStrip Path prefix to strip during extraction. If null, archive paths are extracted as-is.
 * @throws IllegalStateException if archive format is not supported
 */
@ApiStatus.Internal
fun unpackArchive(archiveFile: Path, targetDir: Path, prefixToStrip: String?) {
  val fileName = archiveFile.fileName.toString()
  when {
    fileName.endsWith(".tar.gz", ignoreCase = true) || fileName.endsWith(".tgz", ignoreCase = true) -> {
      val decompressor = Decompressor.Tar(archiveFile)
      if (prefixToStrip != null) {
        decompressor.removePrefixPath(prefixToStrip)
      }
      decompressor.extract(targetDir)
    }
    fileName.endsWith(".zip", ignoreCase = true) -> {
      val decompressor = Decompressor.Zip(archiveFile).withZipExtensions()
      if (prefixToStrip != null) {
        decompressor.removePrefixPath(prefixToStrip)
      }
      decompressor.extract(targetDir)
    }
    else -> {
      error("Unsupported archive format: $fileName. Expected .tar.gz, .tgz, or .zip")
    }
  }
}