// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management.ui

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.findPackageSpecification
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import com.jetbrains.python.util.ShowingMessageErrorSync
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * A class responsible for managing Python packages within a specific SDK and project.
 * Provides functionality for reloading, installing, updating, and uninstalling Python packages.
 *
 * This class is used for work with packages with the process and handling all execution errors and providing background process
 * All functions return the calculated result or null if the operation was failed.
 * Prefer this class for external usage
 *
 * @see PythonPackageManager if you need more control over the process and handling errors
 *
 * @constructor Creates an instance of the PythonPackageManagerUI class.
 * @param project The project within which the operations are performed.
 * @param sdk The Python SDK associated with the operations.
 * @param sink The error sink used for reporting errors during operations.
 */
@ApiStatus.Internal
class PythonPackageManagerUI(val manager: PythonPackageManager, val sink: ErrorSink = ShowingMessageErrorSync) {
  val project: Project = manager.project
  val sdk: Sdk = manager.sdk

  /**
   * @return List of installed packages or null if the operation was failed.
   */
  suspend fun reloadPackagesBackground(): List<PythonPackage>? {
    return executeCommand(PyBundle.message("python.packaging.list.packages")) {
      manager.reloadPackages()
    }
  }

  suspend fun installWithConfirmation(packages: List<String>, module: Module? = null): List<PythonPackage>? {
    val requirements = packages.map { pyRequirement(it) }
    return installPyRequirementsWithConfirmation(requirements, module)
  }

  suspend fun installPyRequirementsWithConfirmation(
    packages: List<PyRequirement>,
    module: Module? = null,
  ): List<PythonPackage>? {
    val confirmed = PyPackageManagerUiConfirmationHelpers.getConfirmedPackages(packages, project)
    if (confirmed.isEmpty())
      return null

    PyPackagesUsageCollector.installAllEvent.log(confirmed.size)
    return installPyRequirementsBackground(confirmed, module = module)
  }

  suspend fun installPyRequirementsDetachedWithConfirmation(packages: List<PyRequirement>): List<PythonPackage>? {
    val confirmed = PyPackageManagerUiConfirmationHelpers.getConfirmedPackages(packages, project)
    if (confirmed.isEmpty())
      return null

    PyPackagesUsageCollector.installAllEvent.log(confirmed.size)
    return installPyRequirementsDetachedBackground(confirmed)
  }

  /**
   * @return List of all installed packages or null if the operation was failed.
   */
  suspend fun installPackagesRequestBackground(
    installRequest: PythonPackageInstallRequest,
    options: List<String> = emptyList(),
    module: Module? = null,
  ): List<PythonPackage>? {
    return executeCommand(getProgressTitle(installRequest)) {
      manager.installPackage(installRequest, options, module)
    }
  }

  suspend fun installPackagesRequestDetachedBackground(
    installRequest: PythonPackageInstallRequest,
    options: List<String> = emptyList(),
  ): List<PythonPackage>? {
    return executeCommand(getProgressTitle(installRequest)) {
      manager.installPackageDetached(installRequest, options)
    }
  }

  private fun getProgressTitle(installRequest: PythonPackageInstallRequest): @Nls String {
    return when (installRequest) {
      is PythonPackageInstallRequest.ByLocation -> PyBundle.message("python.packaging.installing.package", installRequest.title)
      is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> if (installRequest.specifications.size == 1) {
        PyBundle.message("python.packaging.installing.package", installRequest.specifications.first().name)
      }
      else {
        PyBundle.message("python.packaging.installing.packages")
      }
    }
  }

  /**
   * @return List of all installed packages or null if the operation was failed.
   */
  suspend fun updatePackagesBackground(
    packages: List<PythonRepositoryPackageSpecification>,
  ): List<PythonPackage>? {
    val progressTitle = if (packages.size > 1) {
      PyBundle.message("python.packaging.updating.packages")
    }
    else {
      PyBundle.message("python.packaging.updating.package", packages.first().name)
    }

    return executeCommand(progressTitle) {
      manager.updatePackages(*packages.toTypedArray())
    }
  }

  /**
   * @return List of all installed packages or null if the operation was failed.
   */
  suspend fun uninstallPackagesBackground(
    packages: List<String>,
    workspaceMember: PyWorkspaceMember? = null,
  ): List<PythonPackage>? {
    val progressTitle = if (packages.size > 1) {
      PyBundle.message("python.packaging.uninstall.packages")
    }
    else {
      PyBundle.message("python.packaging.uninstall.package", packages.first())
    }

    return executeCommand(progressTitle
    ) {
      manager.uninstallPackage(*packages.toTypedArray(), workspaceMember = workspaceMember)
    }
  }

  @ApiStatus.Internal
  suspend fun <T> executeCommand(
    progressTitle: @Nls String,
    operation: suspend (() -> PyResult<T>),
  ): T? = PythonPackageManagerUIHelpers.runPackagingOperationMaybeBackground(manager, sink, progressTitle) {

    operation()
  }

  /**
   * Installs packages by name using modal progress, blocking the calling EDT thread.
   *
   * Resolves package specifications from repository, then installs them while showing
   * a modal progress dialog. Errors are reported via [sink].
   *
   * Intended for use from modal dialogs (e.g., Settings) where background progress is not visible.
   *
   * @return list of all installed packages after installation, or null if the operation failed
   */
  @RequiresEdt
  @RequiresBlockingContext
  fun installPackagesWithModalProgressBlocking(vararg packages: String): List<PythonPackage>? {
    val specifications = runWithModalProgressBlocking(project, PyBundle.message("python.packaging.installing.packages")) {
      packages.mapNotNull {
        manager.findPackageSpecification(it)
      }
    }

    val installRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specifications)
    val title = getProgressTitle(installRequest)
    return runWithModalProgressBlocking(project, title) {
      manager.installPackage(installRequest, emptyList()).onFailure {
        sink.emit(it, project)
      }.getOrNull()
    }
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    @ApiStatus.Internal
    fun forSdk(project: Project, sdk: Sdk, sink: ErrorSink = ShowingMessageErrorSync): PythonPackageManagerUI {
      val packageManager = PythonPackageManager.forSdk(project, sdk)
      return forPackageManager(packageManager, sink)
    }

    @ApiStatus.Internal
    fun forPackageManager(
      packageManager: PythonPackageManager,
      sink: ErrorSink = ShowingMessageErrorSync,
    ): PythonPackageManagerUI = PythonPackageManagerUI(packageManager, sink)
  }
}