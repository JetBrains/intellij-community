// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.community.impl.conda.PyCondaBundle
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.binaryPathString
import com.intellij.python.sdk.backend.evolution.toolMissing
import com.intellij.python.sdk.backend.resolveExecutable
import com.intellij.python.sdk.backend.toFileSystem
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManagerEngine
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.conda.condaIdentityOf
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity

/** What runs conda for the env of an interpreter: the conda binary on the machine of the env, and the identity of the env. */
private data class CondaEnvExecution(val conda: BinaryToExec, val env: PyCondaEnvIdentity)

internal class CondaPackageManagerEngine(private val interpreter: PythonInterpreter) : PythonPackageManagerEngine {
  suspend fun updateFromEnvironmentFile(envFile: VirtualFile): PyResult<Unit> {
    val (conda, env) = condaEnvExecution().getOr { return it }
    return CondaExecutor.updateFromEnvironmentFile(conda, envFile.path, env)
  }

  suspend fun exportToEnvironmentFile(): PyResult<String> {
    val (conda, env) = condaEnvExecution().getOr { return it }
    return CondaExecutor.exportEnvironmentFile(conda, env)
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    val (conda, env) = condaEnvExecution().getOr { return it }
    return CondaExecutor.listOutdatedPackages(conda, env)
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    val installationArgs = installRequest.buildCondaInstallationArguments().getOr { return it }
    val (conda, env) = condaEnvExecution().getOr { return it }
    return CondaExecutor.installPackages(conda, env, installationArgs, options)
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    // Pass the caller's constraint through, the same way installPackageCommand does: with a bare name
    // conda updates to the newest version it can resolve, silently ignoring the requested spec.
    // Specs are single argv elements (no shell) and must not be quoted, see PY-91412.
    val packages = specifications.map { it.nameWithVersionSpecs }
    val (conda, env) = condaEnvExecution().getOr { return it }
    return CondaExecutor.installPackages(conda, env, packages, emptyList())
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String, workspaceMember: PyWorkspaceMember?, dependencyGroup: PyDependencyGroup?): PyResult<Unit> {
    if (pythonPackages.isEmpty())
      return PyResult.success(Unit)

    val (conda, env) = condaEnvExecution().getOr { return it }
    return CondaExecutor.uninstallPackages(conda, env, pythonPackages.toList())
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    val (conda, env) = condaEnvExecution().getOr { return it }
    return CondaExecutor.listPackages(conda, env)
  }

  /** The conda binary and the env of [interpreter], from its ref and its Python binary, see [condaIdentityOf]. */
  private suspend fun condaEnvExecution(): PyResult<CondaEnvExecution> =
    condaEnvExecutionOn(interpreter.toFileSystem())

  private suspend fun <P : PathHolder> condaEnvExecutionOn(fileSystem: FileSystem<P>): PyResult<CondaEnvExecution> {
    val tool = CondaPyTool.getInstance()
    val conda = fileSystem.resolveExecutable(tool) ?: return toolMissing(tool)
    val pythonBinary = fileSystem.parsePath(interpreter.binaryPathString).getOr { return it }
    val env = condaIdentityOf(fileSystem, interpreter.ref.envRef, pythonBinary)
    return PyResult.success(CondaEnvExecution(fileSystem.getBinaryToExec(conda), env))
  }
}

internal fun PythonPackageInstallRequest.buildCondaInstallationArguments(): PyResult<List<String>> = when (this) {
  is PythonPackageInstallRequest.ByLocation -> PyResult.localizedError(PyCondaBundle.message("python.packaging.conda.does.not.support.location.uri"))
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