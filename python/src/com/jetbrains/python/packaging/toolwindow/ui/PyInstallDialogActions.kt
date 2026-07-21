// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.Args
import com.intellij.python.pyproject.model.api.getPyProjectTomlFile
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.executeOn
import com.jetbrains.python.Result as PyR
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.pyRequirementVersionSpec
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.InstallOptions
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.moduleIfExists
import kotlinx.io.IOException
import java.net.URI

/**
 * Headless install actions used by [PyInstallPackageDialog]. The dialog only collects user input
 * (selected package, version, editable flag, dependency group, target module) and delegates the
 * actual `PythonPackageManagerUI.installPackagesRequestBackground` call here, so the install
 * pipeline (request → CLI args → reload → refresh `pyproject.toml`) lives outside the Swing class
 * and can be invoked from anywhere — including future non-dialog entry points.
 */

/**
 * Installs [packageName] (optionally pinned to [version]) from [repository] into [sdk]. Returns
 * `true` on success, `false` if the background install task was cancelled or the package manager
 * UI refused to run.
 */
internal suspend fun installPackageFromRepository(
  service: PyPackagingToolWindowService,
  sdk: Sdk,
  repository: PyPackageRepository,
  packageName: PyPackageName,
  version: PyPackageVersion?,
  editable: Boolean,
  dependencyGroup: PyDependencyGroup?,
  moduleOrProject: ModuleOrProject,
): Boolean {
  val versionSpec = version?.let { pyRequirementVersionSpec(PyRequirementRelation.EQ, it) }
  val spec = PythonRepositoryPackageSpecification(repository = repository, requirement = pyRequirement(packageName.name, versionSpec))
  val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(spec))
  return runInstall(service, sdk, request, editable, dependencyGroup, moduleOrProject)
}

/** Installs whatever lives at [location] (URL / local path) into [sdk]. */
internal suspend fun installPackageFromLocation(
  service: PyPackagingToolWindowService,
  sdk: Sdk,
  location: URI,
  editable: Boolean,
  dependencyGroup: PyDependencyGroup?,
  moduleOrProject: ModuleOrProject,
): Boolean = runInstall(
  service = service,
  sdk = sdk,
  request = PythonPackageInstallRequest.ByLocation(location),
  editable = editable,
  dependencyGroup = dependencyGroup,
  moduleOrProject = moduleOrProject,
)

private suspend fun runInstall(
  service: PyPackagingToolWindowService,
  sdk: Sdk,
  request: PythonPackageInstallRequest,
  editable: Boolean,
  dependencyGroup: PyDependencyGroup?,
  moduleOrProject: ModuleOrProject,
): Boolean {
  val options = InstallOptions(
    editable = editable,
    dependencyGroup = dependencyGroup?.takeIf { it.name.isNotEmpty() },
  )
  val cliArgs = options.toCliArgs(sdk, moduleOrProject)
  val module = (moduleOrProject as? ModuleOrProject.ModuleAndProject)?.module
  // Group flag already baked into cliArgs via toCliArgs; skip the separate [dependencyGroup] hook
  // so the SDK low-level layer doesn't re-emit the flag.
  PythonPackageManagerUI.forSdk(moduleOrProject.project, sdk).installPackagesRequestBackground(request, cliArgs, module)
    ?: return false
  service.reloadPackages()
  refreshModulePyprojectToml(moduleOrProject)
  return true
}

/**
 * Refresh the module's `pyproject.toml` after a successful install so the editor reflects the
 * new dependency entry right away. uv / Poetry write the file from outside the IDE process, so
 * without an explicit VFS refresh the in-memory copy lags behind the disk for a beat.
 */
private suspend fun refreshModulePyprojectToml(moduleOrProject: ModuleOrProject) {
  val module = moduleOrProject.moduleIfExists ?: return
  val file = module.getPyProjectTomlFile() ?: return
  file.refresh(true, false)
}

/**
 * Outcome of [runCliCommand]. Sealed interface so callers pattern-match with an exhaustive
 * `when` on the exact failure and produce their own user-facing message (the actions layer stays
 * UI-free). No `as?` casts — variants carry their payload directly.
 */
internal sealed interface CliCommandResult {
  object Success : CliCommandResult
  data class ExecutableNotFound(val toolName: String) : CliCommandResult
  data class IoFailure(val exception: IOException) : CliCommandResult
}

/**
 * Runs [tool] via [PyTool.executeOn] against [moduleOrProject]. Delegating to [PyTool] gives us
 * OS-specific binary names (`.exe` on Windows), remote / target-based SDKs and PATH resolution
 * for free — the actions layer does not have to reinvent any of it.
 *
 * Off-EDT: [PyTool.executeOn] hops to [Dispatchers.IO] internally.
 */
internal suspend fun runCliCommand(
  moduleOrProject: ModuleOrProject,
  service: PyPackagingToolWindowService,
  tool: PyTool,
  args: List<String>,
): CliCommandResult =
  when (val result = tool.executeOn(moduleOrProject, Args(*args.toTypedArray()))) {
    is PyR.Success -> {
      service.reloadPackages()
      CliCommandResult.Success
    }
    is PyR.Failure -> CliCommandResult.IoFailure(IOException(result.error.message))
  }
