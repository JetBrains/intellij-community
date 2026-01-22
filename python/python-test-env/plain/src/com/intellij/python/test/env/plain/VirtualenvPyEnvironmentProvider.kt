// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.plain

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.python.community.execService.asBinToExec
import com.intellij.python.community.impl.venv.createVenv
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.core.PyEnvironmentProvider
import com.intellij.python.test.env.core.installPipPackages
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.venvReader.VirtualEnvReader
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * Environment provider for plain Python with venv.
 * 
 * This provider:
 * 1. Downloads Python from python-build-standalone for the current OS
 * 2. Extracts Python to workingDir/python/<version>/
 * 3. Creates a virtual environment using Python's built-in venv module
 * 4. Installs required libraries using pip
 * 5. Returns a PyEnvironment abstraction for further operations
 * 
 * Downloads Python from: https://github.com/astral-sh/python-build-standalone/releases
 * 
 */
@ApiStatus.Internal
class VirtualenvPyEnvironmentProvider : PyEnvironmentProvider<VirtualenvPyEnvironmentSpec>("venv") {

  override suspend fun setupEnvironment(context: Context, spec: VirtualenvPyEnvironmentSpec): PyEnvironment {
    val logger = thisLogger()
    val targetPath = nextEnvPath(context.workingDir)
    
    logger.info("Setting up virtual environment at: $targetPath")
    logger.info("Python version: ${spec.pythonVersion}")

    logger.info("Creating base Python environment")
    val basePythonEnvironment = context.factory.createEnvironment(pythonEnvironment { pythonVersion = spec.pythonVersion })
    logger.info("Base Python path: ${basePythonEnvironment.pythonPath}")

    logger.info("Creating virtual environment")
    createVenv(
      python = basePythonEnvironment.pythonPath.asBinToExec(),
      venvDir = targetPath.toString(),
      inheritSitePackages = false
    ).getOrThrow()
    
    val venvPython = VirtualEnvReader().findPythonInPythonRoot(targetPath)
                     ?: error("Failed to find Python executable in virtual environment directory: $targetPath")
    logger.info("Virtual environment Python path: $venvPython")

    if (spec.libraries.isNotEmpty()) {
      logger.info("Installing libraries: ${spec.libraries.joinToString(", ")}")
      installLibraries(venvPython, spec.libraries)
      logger.info("Libraries installed successfully")
    }

    logger.info("Virtual environment setup completed at: $targetPath")
    return VirtualenvPyEnvironment(venvPython, targetPath)
  }

  private suspend fun installLibraries(pythonPath: Path, libraries: List<String>) {
    installPipPackages(pythonPath, libraries, thisLogger())
  }

  /**
   * Plain Python environment implementation
   */
  private class VirtualenvPyEnvironment(
      override val pythonPath: PythonBinary,
      override val envPath: Path,
  ) : PyEnvironment {
    @OptIn(ExperimentalPathApi::class)
    override fun close() {
      envPath.deleteRecursively()
    }
  }
}
