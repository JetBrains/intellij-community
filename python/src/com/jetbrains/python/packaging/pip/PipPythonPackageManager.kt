// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonHelpersLocator.Companion.findPathInHelpers
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.management.runPackagingTool
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.statistics.version
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private fun PythonRepositoryPackageSpecification.buildPipInstallArguments(): List<String> = buildList {
  add(nameWithVersionSpec)
  val urlForInstallation = repository.urlForInstallation.toString()
  urlForInstallation.takeIf { it.isNotBlank() && it != PyPIPackageUtil.PYPI_LIST_URL }?.let {
    add("--index-url")
    add(it)
  }
}

private fun PythonPackageInstallRequest.buildPipInstallArguments(): List<String> = when (this) {
  is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecification -> this.specification.buildPipInstallArguments()
  is PythonPackageInstallRequest.ByLocation -> listOf(location.toString())
  is PythonPackageInstallRequest.AllRequirements -> emptyList()
}

@ApiStatus.Experimental
@ApiStatus.Internal
open class PipPythonPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  @Volatile
  override var installedPackages: List<PythonPackage> = emptyList()
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager(project)

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): Result<Unit> {
    PipManagementInstaller(sdk, this).installManagementIfNeeded()
    try {
      runPackagingTool("install", installRequest.buildPipInstallArguments() + options, PyBundle.message("python.packaging.install.progress", installRequest.title), withBackgroundProgress = false)
    }
    catch (ex: ExecutionException) {
      return Result.failure(ex)
    }

    return Result.success(Unit)
  }

  override suspend fun updatePackageCommand(specification: PythonRepositoryPackageSpecification): Result<Unit> {
    try {
      runPackagingTool("install", listOf("--upgrade") + specification.buildPipInstallArguments(), PyBundle.message("python.packaging.update.progress", specification.name))
    }
    catch (ex: ExecutionException) {
      return Result.failure(ex)
    }

    return Result.success(Unit)
  }

  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<Unit> {
    try {
      runPackagingTool("uninstall", listOf(pkg.name), PyBundle.message("python.packaging.uninstall.progress", pkg.name))
    }
    catch (ex: ExecutionException) {
      return Result.failure(ex)
    }

    return Result.success(Unit)
  }

  override suspend fun reloadPackagesCommand(): Result<List<PythonPackage>> {
    try {
      val output = runPackagingTool("list", emptyList(), PyBundle.message("python.packaging.list.progress"))
      val packages = output.lineSequence()
        .filter { it.isNotBlank() }
        .map {
          val line = it.split("\t")
          PythonPackage(line[0], line[1], isEditableMode = false)
        }
        .sortedWith(compareBy(PythonPackage::name))
        .toList()

      return Result.success(packages)
    }
    catch (ex: ExecutionException) {
      return Result.failure(ex)
    }
  }
}

@ApiStatus.Internal
class PipManagementInstaller(private val sdk: Sdk, private val manager: PythonPackageManager) {
  private val languageLevel: LanguageLevel = sdk.version

  fun installManagementIfNeeded(): Boolean {
    if (hasManagement()) return true
    return performManagementInstallation()
  }

  private fun performManagementInstallation(): Boolean = installManagement()

  fun hasManagement(): Boolean =
    languageLevel < LanguageLevel.PYTHON27 || (manager.packageExists(PIP_PACKAGE) && hasSetuptools())

  private fun installManagement(): Boolean =
    installWheelIfMissing(::hasPip, WheelFiles.PIP_WHEEL_NAME) &&
    installWheelIfMissing(::hasSetuptools, WheelFiles.SETUPTOOLS_WHEEL_NAME)

  private fun installWheelIfMissing(requirementCheck: () -> Boolean, wheelNameToInstall: String): Boolean {
    if (!requirementCheck()) {
      val wheelPathToInstall = findPathInHelpers(wheelNameToInstall)?.toString() ?: return false
      return installUsingPipWheel("--no-index", wheelPathToInstall)
    }
    return true
  }

  private fun installUsingPipWheel(vararg additionalArgs: String): Boolean {
    val pipWheelPath = findPathInHelpers(WheelFiles.PIP_WHEEL_NAME)?.resolve(Path.of(PyPackageUtil.PIP))
    if (pipWheelPath == null) return false
    val commandArguments = buildCommandArguments(pipWheelPath, *additionalArgs)
    return executeCommand(commandArguments)
  }

  private fun executeCommand(commandArguments: List<String>): Boolean =
    try {
      val processHandler = CapturingProcessHandler(GeneralCommandLine(commandArguments))
      val output: ProcessOutput = processHandler.runProcess()
      output.exitCode == 0
    }
    catch (ex: Exception) {
      throw ExecutionException(ex.message, ex)
    }

  private fun hasPip(): Boolean = manager.packageExists(PIP_PACKAGE)

  private fun hasSetuptools(): Boolean =
    languageLevel >= LanguageLevel.PYTHON312 ||
    manager.packageExists(SETUPTOOLS_PACKAGE) ||
    manager.packageExists(DISTRIBUTE_PACKAGE)

  private fun buildCommandArguments(wheelPath: Path, vararg additionalArgs: String): List<String> =
    listOfNotNull(sdk.homePath.toString(), wheelPath.toString(), "install") + additionalArgs

  private object WheelFiles {
    const val SETUPTOOLS_WHEEL_NAME = "setuptools-44.1.1-py2.py3-none-any.whl"
    const val PIP_WHEEL_NAME = "pip-24.3.1-py2.py3-none-any.whl"
  }

  companion object {
    val PIP_PACKAGE: PythonPackage = PythonPackage(PyPackageUtil.PIP, "24.3.1", false)
    val SETUPTOOLS_PACKAGE: PythonPackage = PythonPackage(PyPackageUtil.SETUPTOOLS, "44.1.1", false)
    val DISTRIBUTE_PACKAGE: PythonPackage = PythonPackage(PyPackageUtil.DISTRIBUTE, "", false)
  }
}