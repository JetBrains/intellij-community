// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeInformation

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.PythonExecuteUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal class SageMathTypeInformationGenerator : PyTypeInformationGenerator {
  override val presentableName: String = "SageMath"
  override val enginePackageName: String = ENGINE_PACKAGE

  override suspend fun isApplicable(packageManager: PythonPackageManager): Boolean {
    return hasSageMathDistribution(packageManager.listInstalledPackages().map { it.name })
  }

  override suspend fun generate(project: Project, sdk: Sdk): PyTypeInformationGenerationResult {
    val installation = execute(project, sdk, "pip", listOf("install", "--upgrade", ENGINE_PACKAGE), INSTALL_TIMEOUT)
    if (!installation.isSuccessful) {
      return PyTypeInformationGenerationResult.Failure(
        PyTypeInformationGenerationResult.Stage.INSTALL_ENGINE,
        installation.failureDetails(),
      )
    }

    val generation = execute(project, sdk, ENGINE_MODULE, listOf("--install"), GENERATION_TIMEOUT)
    if (!generation.isSuccessful) {
      return PyTypeInformationGenerationResult.Failure(
        PyTypeInformationGenerationResult.Stage.GENERATE,
        generation.failureDetails(),
      )
    }

    return PyTypeInformationGenerationResult.Success
  }

  private suspend fun execute(
    project: Project,
    sdk: Sdk,
    module: String,
    arguments: List<String>,
    timeout: Duration,
  ): ProcessOutput = PythonExecuteUtils.executePyModuleScript(
    project = project,
    sdk = sdk,
    pyModuleToRun = module,
    runArgs = arguments,
    timeout = timeout,
  )

  companion object {
    internal const val ENGINE_PACKAGE = "sage-pycharm-stubgen"
    internal const val ENGINE_MODULE = "sage_pycharm_stubgen"
    private val INSTALL_TIMEOUT = 10.minutes
    private val GENERATION_TIMEOUT = 60.minutes
    private val SAGE_DISTRIBUTIONS = setOf("sage", "sagemath", "sagemath-standard")

    internal fun hasSageMathDistribution(packageNames: Iterable<String>): Boolean {
      return packageNames.any { normalizePackageName(it) in SAGE_DISTRIBUTIONS }
    }

    internal fun normalizePackageName(name: String): String {
      return name.lowercase().replace('_', '-').replace('.', '-')
    }
  }
}

private val ProcessOutput.isSuccessful: Boolean
  get() = !isTimeout && exitCode == 0

private fun ProcessOutput.failureDetails(): String {
  if (isTimeout) return "Process timed out"
  return stderr.ifBlank { stdout }.trim().ifBlank { "Process exited with code $exitCode" }
}
