// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.uv

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.python.test.env.core.CacheKey
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.core.PyEnvironmentProvider
import com.intellij.python.test.env.core.PyEnvironmentSpec
import com.intellij.python.test.env.core.executeProcess
import com.intellij.util.system.OS
import com.jetbrains.python.PythonBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Specification for UV Python environment with mandatory version pinning.
 * 
 * @property uvVersion Specific UV version (e.g., "0.5.11")
 */
@ApiStatus.Internal
class UvPyEnvironmentSpec(
  val uvVersion: String,
) : PyEnvironmentSpec<UvPyEnvironmentSpec>() {

  override fun toCacheKey(): CacheKey {
    return buildCacheKey("uv", "uv:$uvVersion")
  }
}

/**
 * DSL entry point for creating uv environment specifications.
 *
 * Version must be specified explicitly - no default or "latest" option.
 *
 * Example:
 * ```kotlin
 * val env = uvEnvironment("0.9.21") {
 *   pythonVersion = LATEST_PYTHON_VERSION
 *   libraries {
 *     +"numpy==2.0.2"
 *   }
 * }
 * ```
 */
@ApiStatus.Internal
fun uvEnvironment(uvVersion: String, block: UvPyEnvironmentSpec.() -> Unit): UvPyEnvironmentSpec {
  val spec = UvPyEnvironmentSpec(uvVersion)
  spec.block()
  return spec
}

/**
 * Environment provider for UV-managed Python environments.
 * 
 * This provider:
 * 1. Downloads UV binary for the current OS
 * 2. Unpacks it to workingDir/uv/<version>/
 * 3. Uses UV to download and setup Python with the requested version
 * 4. Installs required libraries using UV
 * 5. Creates an SDK from the environment
 *
 */
@ApiStatus.Internal
class UvPyEnvironmentProvider : PyEnvironmentProvider<UvPyEnvironmentSpec>("uv") {

  override suspend fun setupEnvironment(context: Context, spec: UvPyEnvironmentSpec): PyEnvironment {
    val logger = thisLogger()
    val targetPath = nextEnvPath(context.workingDir)

    logger.info("Setting up UV environment at: $targetPath")
    logger.info("UV version: ${spec.uvVersion}")
    logger.info("Python version: ${spec.pythonVersion}")

    val uvExecutable = downloadAndSetupUv(spec)
    logger.info("UV executable: $uvExecutable")

    val fullPythonVersion = spec.pythonVersion.toString()

    logger.info("Installing Python with UV")
    val pythonPath = installPythonWithUv(uvExecutable, targetPath, fullPythonVersion)
    logger.info("UV Python path: $pythonPath")

    if (spec.libraries.isNotEmpty()) {
      logger.info("Installing libraries: ${spec.libraries.joinToString(", ")}")
      installLibraries(uvExecutable, pythonPath, spec.libraries)
      logger.info("Libraries installed successfully")
    }

    logger.info("UV environment setup completed at: $targetPath")
    return UvPyEnvironment(pythonPath, targetPath, uvExecutable)
  }

  private suspend fun installLibraries(uvExecutable: Path, pythonPath: Path, libraries: List<String>) {
    val logger = thisLogger()

    if (libraries.isEmpty()) {
      logger.info("No libraries to install")
      return
    }

    val command = listOf(uvExecutable.pathString, "pip", "install", "--python", pythonPath.pathString) + libraries
    executeProcess(command, logger, "uv pip")
  }

  /**
   * Downloads and unpacks UV binary for the current OS to cache directory.
   * Returns path to UV executable.
   */
  private suspend fun downloadAndSetupUv(spec: UvPyEnvironmentSpec): Path {
    return getOrDownloadUvExecutable(spec.uvVersion)
  }

  /**
   * Uses UV to install Python with the specified version.
   */
  private suspend fun installPythonWithUv(
    uvExecutable: Path,
    envPath: Path,
    pythonVersion: String,
  ): Path {
    val logger = thisLogger()
    return withContext(Dispatchers.IO) {
      logger.info("Creating environment directory: $envPath")
      envPath.createDirectories()

      // Use uv to create a venv with specific Python version
      // uv will download Python if not already available
      val command = listOf(
        uvExecutable.pathString,
        "venv",
        envPath.pathString,
        "--python",
        pythonVersion
      )

      executeProcess(command, logger, "uv venv")

      logger.info("UV venv created successfully")

      // Find Python executable in created venv
      logger.info("Looking for Python executable in: $envPath")
      val pythonPath = if (OS.CURRENT == OS.Windows) {
        envPath.resolve("Scripts").resolve("python.exe")
      }
      else {
        envPath.resolve("bin").resolve("python")
      }

      if (!pythonPath.exists()) {
        error("Python executable not found in UV venv: ${pythonPath.pathString}")
      }

      logger.info("Found Python executable: $pythonPath")

      pythonPath
    }
  }

}

class UvPyEnvironment(
  override val pythonPath: PythonBinary,
  override val envPath: Path,
  val uvExecutable: Path
) : PyEnvironment {
  @OptIn(ExperimentalPathApi::class)
  override fun close() {
    envPath.deleteRecursively()
  }
}
