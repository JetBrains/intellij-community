// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import com.intellij.python.sdk.backend.PIP_MANAGER
import com.intellij.python.sdk.backend.buildTestInterpreter
import com.intellij.python.sdk.backend.addTestInterpreter
import com.intellij.openapi.project.Project
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.intellij.python.sdk.backend.pathEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.UUID

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

  /**
   * The interpreter of this environment in [project], for a test without a `PyProject`. [envPath] owns it. It is in
   * the registry of [project]: remove it with `removeTestInterpreter`.
   */
  suspend fun prepareInterpreter(project: Project): PythonInterpreter = withContext(Dispatchers.IO) {
    // The environment belongs to no module, and its working directory is the env dir rather than a module base dir:
    // drop the association the constructor derives from it. Fixtures that want one call setAssociationToModule.
    val data = PythonSdkAdditionalData(osSpecificSdkFlavorAndData, envPath).apply { associatedModulePath = null }
    val registry = PythonInterpreterRegistry.getInstance(project)
    registry.addTestInterpreter(registry.buildTestInterpreter(envPath, PathHolder.Eel(pythonPath), data).orThrow())
  }

  /**
   * The interpreter of this environment for [pyProject], by its `pip` ref. It is not registered yet: set it with
   * `PyProject.setPythonInterpreter`. Remove it with [PythonInterpreterRegistry.remove].
   */
  suspend fun prepareSdk(pyProject: PyProject): PythonInterpreter =
    PythonInterpreterRegistry.getInstance(pyProject.project)
      .build(pyProject, PyInterpreterRef.native(PIP_MANAGER, pathEnvRef(PathHolder.Eel(pythonPath), pyProject.baseDir)))
      .orThrow()

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
  val supportedSpecType: Class<S>,
) {
  /**
   * Generate next environment directory path within the working directory.
   * Uses provider-specific prefix to avoid conflicts between different provider types.
   */
  protected fun nextEnvPath(workingDir: Path): Path {
    return workingDir.resolve("${envPrefix}_${UUID.randomUUID()}")
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
   * Extract archive using the root directory used by Python archives.
   * Supports tar.gz, .tgz, and .zip formats.
   *
   * @param archiveFile Path to the archive file
   * @param targetDir Directory where archive contents will be extracted
   * @throws IllegalArgumentException if archive format is not supported
   */
  protected fun unpackArchive(archiveFile: Path, targetDir: Path) {
    unpackArchive(archiveFile, targetDir, prefixToStrip = "python")
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
