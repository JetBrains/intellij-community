// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManagerEngine
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import com.jetbrains.python.sdk.getOrCreateAdditionalData

internal class CondaPackageManagerEngine(private val sdk: Sdk) : PythonPackageManagerEngine {
  suspend fun updateFromEnvironmentFile(envFile: VirtualFile): PyResult<Unit> {
    val env = getEnvData()
    return CondaExecutor.updateFromEnvironmentFile(env.condaPath, envFile.path)
  }

  suspend fun exportToEnvironmentFile(): PyResult<String> {
    val env = getEnvData()
    return CondaExecutor.exportEnvironmentFile(env.condaPath, env.envIdentity)
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    val env = getEnvData()
    return CondaExecutor.listOutdatedPackages(env.condaPath, env.envIdentity)
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    val installationArgs = installRequest.buildInstallationArguments().getOr { return it }
    val env = getEnvData()
    return CondaExecutor.installPackages(env.condaPath, env.envIdentity, installationArgs, options)
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    val packages = specifications.map { it.name }
    val env = getEnvData()
    return CondaExecutor.installPackages(env.condaPath, env.envIdentity, packages, emptyList())
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit> {
    if (pythonPackages.isEmpty())
      return PyResult.success(Unit)

    val env = getEnvData()
    return CondaExecutor.uninstallPackages(env.condaPath, env.envIdentity, pythonPackages.toList())
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    val env = getEnvData()
    return CondaExecutor.listPackages(env.condaPath, env.envIdentity)
  }


  private fun getEnvData(): PyCondaEnv = (sdk.getOrCreateAdditionalData().flavorAndData.data as PyCondaFlavorData).env

  private fun PythonPackageInstallRequest.buildInstallationArguments(): PyResult<List<String>> = when (this) {
    is PythonPackageInstallRequest.ByLocation -> PyResult.localizedError("CondaManager does not support installing from location uri")
    is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> {
      val condaSpecs = specifications.filter { it.repository is CondaPackageRepository }
      val specs = condaSpecs.map { it.nameWithVersionSpec }

      //https://docs.conda.io/projects/conda/en/latest/user-guide/concepts/pkg-specs.html#package-match-specifications
      //When using the command line, put double quotes around any package version specification that
      // contains the space character or any of the following characters: <, >, *, or |.
      //Ido not know why we need put single prefix quota but it does not work in EEL with suffix quota
      val quoted = specs.map { "\"$it" }

      PyResult.success(quoted)
    }
  }

  companion object {

  }
}