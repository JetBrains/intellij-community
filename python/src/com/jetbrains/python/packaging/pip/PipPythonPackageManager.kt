// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PythonHelpersLocator.Companion.findPathInHelpers
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.statistics.version
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Experimental
@ApiStatus.Internal
open class PipPythonPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager(project)
  private val engine = PipPackageManagerEngine(project, sdk)

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> = engine.loadOutdatedPackagesCommand()

  override suspend fun installPackageCommand(
    installRequest: PythonPackageInstallRequest,
    options: List<String>,
  ): PyResult<Unit> = engine.installPackageCommand(installRequest, options)

  override suspend fun updatePackageCommand(
    vararg specifications: PythonRepositoryPackageSpecification,
  ): PyResult<Unit> = engine.updatePackageCommand(*specifications)

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit> = engine.uninstallPackageCommand(*pythonPackages)

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> = engine.loadPackagesCommand()
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
    languageLevel < LanguageLevel.PYTHON27 || (manager.isPackageInstalled(PIP_PACKAGE) && hasSetuptools())

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

  private fun hasPip(): Boolean = manager.isPackageInstalled(PIP_PACKAGE)

  private fun hasSetuptools(): Boolean =
    languageLevel >= LanguageLevel.PYTHON312 ||
    manager.isPackageInstalled(SETUPTOOLS_PACKAGE) ||
    manager.isPackageInstalled(DISTRIBUTE_PACKAGE)

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