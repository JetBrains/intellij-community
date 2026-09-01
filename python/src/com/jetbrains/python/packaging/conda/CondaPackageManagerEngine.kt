// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManagerEngine
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.conda.execution.getCondaBinToExecute
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import com.jetbrains.python.sdk.pySdkAdditionalData

internal class CondaPackageManagerEngine(private val sdk: Sdk) : PythonPackageManagerEngine {
  suspend fun updateFromEnvironmentFile(envFile: VirtualFile): PyResult<Unit> {
    val env = getEnvData()
    return CondaExecutor.updateFromEnvironmentFile(sdk.getCondaBinToExecute(), envFile.path, env.envIdentity)
  }

  suspend fun exportToEnvironmentFile(): PyResult<String> {
    val env = getEnvData()
    return CondaExecutor.exportEnvironmentFile(sdk.getCondaBinToExecute(), env.envIdentity)
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    val env = getEnvData()
    return CondaExecutor.listOutdatedPackages(sdk.getCondaBinToExecute(), env.envIdentity)
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    val installationArgs = installRequest.buildCondaInstallationArguments().getOr { return it }
    val env = getEnvData()
    return CondaExecutor.installPackages(sdk.getCondaBinToExecute(), env.envIdentity, installationArgs, options)
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    // Pass the caller's constraint through, the same way installPackageCommand does: with a bare name
    // conda updates to the newest version it can resolve, silently ignoring the requested spec.
    // Specs are single argv elements (no shell) and must not be quoted, see PY-91412.
    val packages = specifications.map { it.nameWithVersionSpecs }
    val env = getEnvData()
    return CondaExecutor.installPackages(sdk.getCondaBinToExecute(), env.envIdentity, packages, emptyList())
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String, workspaceMember: PyWorkspaceMember?, dependencyGroup: PyDependencyGroup?): PyResult<Unit> {
    if (pythonPackages.isEmpty())
      return PyResult.success(Unit)

    val env = getEnvData()
    return CondaExecutor.uninstallPackages(sdk.getCondaBinToExecute(), env.envIdentity, pythonPackages.toList())
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    val env = getEnvData()
    return CondaExecutor.listPackages(sdk.getCondaBinToExecute(), env.envIdentity)
  }


  private fun getEnvData(): PyCondaEnv = (sdk.pySdkAdditionalData.flavorAndData.data as PyCondaFlavorData).env
}

internal fun PythonPackageInstallRequest.buildCondaInstallationArguments(): PyResult<List<String>> = when (this) {
  is PythonPackageInstallRequest.ByLocation -> PyResult.localizedError(PyBundle.message("python.packaging.conda.does.not.support.location.uri"))
  is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> {
    val condaSpecs = specifications.filter { it.repository is CondaPackageRepository }
    val specs = condaSpecs.map { it.nameWithVersionSpecs }

    // Each spec is passed to conda as a single argv element (no shell), so it must not be quoted.
    // conda's docs recommend double quotes only for shell command lines to protect characters like <, >, *, |.
    // Wrapping the spec in a quote here glues a literal '"' onto the package name, which conda 26.x rejects
    // with InvalidMatchSpec (PY-91412).
    PyResult.success(specs)
  }
}