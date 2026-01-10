package com.intellij.python.test.env.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.system.OS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Extracts archive to target directory if not already extracted (checked via completion marker).
 *
 * This function implements the standard extraction flow:
 * 1. Check if extraction was already completed (marker exists)
 * 2. Clean up incomplete extractions if directory exists without marker
 * 3. Create target directory and call unpack function
 * 4. Create completion marker
 *
 * @param target Target directory for extraction
 * @param logger Logger for operation messages
 * @param unpack Function that performs the actual unpacking to the target directory
 */
@ApiStatus.Internal
@OptIn(ExperimentalPathApi::class)
suspend fun extractIfNecessary(
    target: Path,
    logger: Logger,
    unpack: suspend (Path) -> Unit
) = withContext(Dispatchers.IO) {
    val completionMarker = target.resolve(".extract_complete")

    // Check if already extracted
    if (completionMarker.exists()) {
        logger.info("Already extracted at: $target")
        return@withContext
    }

    // If directory exists but extraction is incomplete, clean it up
    if (target.exists()) {
        logger.info("Cleaning up incomplete extraction at: $target")
        try {
            target.deleteRecursively()
        } catch (e: Exception) {
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