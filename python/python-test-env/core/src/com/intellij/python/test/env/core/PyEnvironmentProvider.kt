// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.Decompressor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Represents a configured Python environment with operations for managing libraries and SDK creation.
 * 
 * This abstraction encapsulates a Python environment and provides methods to:
 * 1. Install additional libraries
 * 2. Create an SDK from the environment
 * 3. Clean up resources when closed
 */
@ApiStatus.Internal
interface PyEnvironment : AutoCloseable {
  /**
   * Path to the Python executable in this environment
   */
  val pythonPath: PythonBinary

  /**
   * Path to the environment directory
   */
  val envPath: Path

  suspend fun prepareSdk(): Sdk = withContext(Dispatchers.IO) {
    val vfsFile = VfsUtil.findFile(pythonPath, true) ?: error("Cannot find Python executable: ${pythonPath}")
    SdkConfigurationUtil.setupSdk(emptyArray(), vfsFile,
                                  SdkType.findByName(PyNames.PYTHON_SDK_ID_NAME)!!, null, null)
  }

  /**
   * Unwrap this environment to get the concrete implementation type.
   * Useful when the environment is wrapped by caching or other delegation layers.
   * 
   * @return The unwrapped concrete implementation, or null if this environment is not of type [T]
   */
  fun <T : PyEnvironment> unwrap(): T? {
    @Suppress("UNCHECKED_CAST")
    return this as? T
  }

  /**
   * Clean up resources associated with this environment.
   * Default implementation does nothing - override if cleanup is needed.
   */
  override fun close() {}
}

/**
 * Provider for setting up Python test environments.
 * 
 * The provider is created with a working directory where all environments will be created.
 * Each environment is set up in a subdirectory of the working directory.
 * 
 * Implementations are responsible for:
 * 1. Setting up the virtual environment using the appropriate tool (venv/uv/conda)
 * 2. Installing the specified Python version
 * 3. Creating a PyEnvironment abstraction for further operations
 */
@ApiStatus.Internal
abstract class PyEnvironmentProvider<S : PyEnvironmentSpec<S>>(
  /**
   * Prefix for environment directories (e.g., "plain", "uv", "conda")
   */
  private val envPrefix: String,
) {
  private var envCounter = 0

  /**
   * Generate next environment directory path within the working directory.
   * Uses provider-specific prefix to avoid conflicts between different provider types.
   */
  protected fun nextEnvPath(workingDir: Path): Path {
    return workingDir.resolve("${envPrefix}_${++envCounter}")
  }

  /**
   * Set up a Python environment based on the specification.
   * The environment will be created in a subdirectory of the working directory.
   *
   * @param context Context providing access to the factory, working directory, and cache directory
   * @param spec Environment specification with Python version, type, and libraries
   * @return PyEnvironment abstraction for the created environment
   */
  abstract suspend fun setupEnvironment(context: Context, spec: S): PyEnvironment

  /**
   * Extract archive using Decompressor framework.
   * Supports tar.gz, .tgz, and .zip formats.
   * 
   * @param archiveFile Path to the archive file
   * @param targetDir Directory where archive contents will be extracted
   * @param prefixToStrip Path prefix to strip during extraction (e.g., "uv-x86_64-unknown-linux-gnu" for UV archives)
   * @throws IllegalArgumentException if archive format is not supported
   */
  protected fun unpackArchive(archiveFile: Path, targetDir: Path, prefixToStrip: String? = null) {
    val fileName = archiveFile.fileName.toString()
    when {
      fileName.endsWith(".tar.gz", ignoreCase = true) || fileName.endsWith(".tgz", ignoreCase = true) -> {
        val decompressor = Decompressor.Tar(archiveFile)
          .removePrefixPath("python")
        if (prefixToStrip != null) {
          decompressor.removePrefixPath(prefixToStrip)
        }
        decompressor.extract(targetDir)
      }
      fileName.endsWith(".zip", ignoreCase = true) -> {
        // Use withZipExtensions() for proper handling of ZIP metadata (directory flags, symlinks, etc.)
        val decompressor = Decompressor.Zip(archiveFile).withZipExtensions()
          .removePrefixPath("python")
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

  /**
   * Context for setting up Python environments.
   * Provides access to the factory, working directory, and cache directory.
   */
  interface Context {
    /**
     * The factory instance used for creating environments.
     * Can be used to create nested or dependent environments.
     */
    val factory: PyEnvironmentFactory
    
    /**
     * Working directory where environments are created.
     * Each environment typically gets its own subdirectory.
     */
    val workingDir: Path
    
    /**
     * Cache directory for storing downloaded artifacts.
     * Used to avoid re-downloading Python distributions and other resources.
     */
    val cacheDir: Path
  }
}
