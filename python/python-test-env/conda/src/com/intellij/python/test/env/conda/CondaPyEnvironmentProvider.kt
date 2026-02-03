// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.conda

import com.intellij.execution.processTools.getResultStdout
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.test.env.core.CacheKey
import com.intellij.python.test.env.core.PyEnvDownloadCache
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.core.PyEnvironmentProvider
import com.intellij.python.test.env.core.PyEnvironmentSpec
import com.intellij.python.test.env.core.executeProcess
import com.intellij.python.test.env.core.extractIfNecessary
import com.intellij.python.test.env.core.markExecutable
import com.intellij.util.io.awaitExit
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Specification for Conda Python environment with mandatory version pinning.
 * 
 * @property condaVersion Specific Miniconda version (e.g., "py312_24.9.2-0")
 */
@ApiStatus.Internal
class CondaPyEnvironmentSpec(
  val condaVersion: String,
) : PyEnvironmentSpec<CondaPyEnvironmentSpec>() {

  override fun toCacheKey(): CacheKey {
    return buildCacheKey("conda", "conda:$condaVersion")
  }
}

/**
 * DSL entry point for creating Conda environment specifications.
 * 
 * Version must be specified explicitly - no default or "latest" option.
 * 
 * Example:
 * ```kotlin
 * val env = condaEnvironment("py312_24.9.2-0") {
 *   pythonVersion = "3.11"
 *   libraries {
 *     +"numpy==2.0.2"
 *   }
 * }
 * ```
 */
@ApiStatus.Internal
fun condaEnvironment(condaVersion: String, block: CondaPyEnvironmentSpec.() -> Unit): CondaPyEnvironmentSpec {
  val spec = CondaPyEnvironmentSpec(condaVersion)
  spec.block()
  return spec
}

/**
 * Environment provider for Conda-managed Python environments.
 *
 * This provider:
 * 1. Downloads Miniconda for the current OS and architecture
 * 2. Installs Miniconda to workingDir/conda/<version>/
 * 3. Creates a new Conda environment with the requested Python version
 * 4. Installs required libraries using conda/pip
 * 5. Returns a PyEnvironment abstraction for further operations
 *
 */
@ApiStatus.Internal
class CondaPyEnvironmentProvider : PyEnvironmentProvider<CondaPyEnvironmentSpec>("conda") {

  private companion object {
    const val MINICONDA_BASE_URL = "https://repo.anaconda.com/miniconda"
  }

  override suspend fun setupEnvironment(context: Context, spec: CondaPyEnvironmentSpec): PyEnvironment {
    val logger = thisLogger()
    val targetPath = nextEnvPath(context.workingDir)

    logger.info("Setting up Conda environment at: $targetPath")
    logger.info("Conda version: ${spec.condaVersion}")
    logger.info("Python version: ${spec.pythonVersion}")

    val condaExecutable = downloadAndSetupConda(spec.condaVersion)
    logger.info("Conda executable: $condaExecutable")

    val fullPythonVersion = spec.pythonVersion.toString()

    logger.info("Creating Conda environment")
    val binaryToExec = BinOnEel(condaExecutable)
    CondaExecutor.createUnnamedEnv(binaryToExec, targetPath.pathString, fullPythonVersion).getOrThrow()
    logger.info("Conda environment created")

    val pythonPath = findPythonInCondaEnv(targetPath)
    logger.info("Conda Python path: $pythonPath")

    if (spec.libraries.isNotEmpty()) {
      logger.info("Installing libraries: ${spec.libraries.joinToString(", ")}")
      installLibraries(targetPath, condaExecutable, spec.libraries)
      logger.info("Libraries installed successfully")
    }
    
    logger.info("Conda environment setup completed at: $targetPath")
    return CondaPyEnvironment(pythonPath, targetPath, condaExecutable)
  }

  /**
   * Downloads and sets up Miniconda in the cache directory.
   * Directory name includes installer name to handle different OS/architecture variants.
   * @return Path to conda executable
   */
  @OptIn(ExperimentalPathApi::class)
  private suspend fun downloadAndSetupConda(condaVersion: String): Path = withContext(Dispatchers.IO) {
    val logger = thisLogger()
    val installerName = getMinicondaInstallerName(condaVersion)
    val installerBaseName = installerName.substringBeforeLast('.')
    val condaDir = PyEnvDownloadCache.cacheDirectory().resolve("conda").resolve(installerBaseName)
    val condaExecutable = getCondaExecutablePath(condaDir)

    extractIfNecessary(condaDir, logger) {
      val downloadUrl = "$MINICONDA_BASE_URL/$installerName"
      logger.info("Downloading Miniconda installer: $installerName")
      val cachedInstaller = PyEnvDownloadCache.getOrDownload(downloadUrl, installerName)
      logger.info("Miniconda installer downloaded: $cachedInstaller")

      logger.info("Installing Miniconda to: $condaDir")
      installMiniconda(logger, cachedInstaller, condaDir)
      logger.info("Miniconda installation completed")

      if (!condaExecutable.exists()) {
        error("Conda executable not found after installation: $condaExecutable")
      }
      logger.info("Conda setup completed")
    }

    condaExecutable
  }

  /**
   * Get the platform-specific Miniconda installer name.
   */
  private fun getMinicondaInstallerName(version: String): String {
    val arch = when {
      CpuArch.isArm64() -> "arm64"
      CpuArch.isIntel64() -> "x86_64"
      else -> "x86"
    }
    
    return when (OS.CURRENT) {
      OS.Windows -> "Miniconda3-$version-Windows-$arch.exe"
      OS.macOS -> "Miniconda3-$version-MacOSX-$arch.sh"
      OS.Linux -> {
        // Linux uses aarch64 instead of arm64
        val linuxArch = if (CpuArch.isArm64()) "aarch64" else arch
        "Miniconda3-$version-Linux-$linuxArch.sh"
      }
      else -> throw UnsupportedOperationException("Unsupported OS: ${OS.CURRENT}")
    }
  }

  /**
   * Install Miniconda from the downloaded installer.
   */
  private suspend fun installMiniconda(logger: Logger, installerPath: Path, targetDir: Path) = withContext(Dispatchers.IO) {
    val command = if (OS.CURRENT == OS.Windows) {
      // Windows: Run .exe installer in silent mode
      listOf(
        installerPath.pathString,
        "/S",  // Silent install
        "/D=${targetDir.pathString}"  // Install directory
      )
    }
    else {
      markExecutable(logger, installerPath)
      listOf(
        "sh",
        installerPath.pathString,
        "-b",  // Batch mode (no prompts)
        "-p", targetDir.pathString  // Install prefix
      )
    }
    
    executeProcess(command, thisLogger(), "miniconda installer")
  }

  /**
   * Get the path to conda executable based on installation directory.
   */
  private fun getCondaExecutablePath(condaDir: Path): Path {
    return if (OS.CURRENT == OS.Windows) {
      condaDir.resolve("Scripts").resolve("conda.exe")
    }
    else {
      condaDir.resolve("bin").resolve("conda")
    }
  }

  /**
   * Find Python executable in a Conda environment directory.
   */
  private fun findPythonInCondaEnv(envPath: Path): Path {
    val pythonPath = if (OS.CURRENT == OS.Windows) {
      envPath.resolve("python.exe")
    }
    else {
      envPath.resolve("bin/python")
    }

    return if (pythonPath.exists()) pythonPath else error("Failed to find Python executable in created Conda environment")
  }

  private suspend fun installLibraries(envPath: Path, condaExecutable: Path, libraries: List<String>) {
    val logger = thisLogger()
    
    if (libraries.isEmpty()) {
      logger.info("No libraries to install")
      return
    }

    val args = mutableListOf("install", "-p", envPath.pathString, "-y")
    args.addAll(libraries)

    val command = listOf(condaExecutable.pathString) + args
    executeProcess(command, logger, "conda")
  }
}

/**
 * Conda Python environment implementation
 */
@ApiStatus.Internal
class CondaPyEnvironment(
    override val pythonPath: PythonBinary,
    override val envPath: Path,
    val condaExecutable: Path,
) : PyEnvironment {
  override fun close() {
    runBlocking(Dispatchers.IO) {
      val args = arrayOf(condaExecutable.toString(), "remove", "-p", envPath.pathString, "--all", "-y")
      val exec = Runtime.getRuntime().exec(args)
      launch {
        try {
          exec.awaitExit()
        }
        catch (e: CancellationException) {
          exec.destroyForcibly()
          throw e
        }
      }
      exec.getResultStdout().getOrElse {
        thisLogger().warn(it)
      }
    }
  }

  override suspend fun prepareSdk(): Sdk {
    // Save a path to conda because some legacy code might use it instead of a full conda path from additional data
    PyCondaPackageService.onCondaEnvCreated(condaExecutable.pathString)
    return PyCondaEnv(
      envIdentity = PyCondaEnvIdentity.UnnamedEnv(envPath.pathString, isBase = true),
      fullCondaPathOnTarget = condaExecutable.toString(),
    ).createSdkFromThisEnv(null, emptyList())
  }
}
