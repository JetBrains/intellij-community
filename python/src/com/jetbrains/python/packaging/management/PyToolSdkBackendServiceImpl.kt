// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.pyproject.PyDependencyGroupKind as ModelDependencyGroupKind
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyToolSdkBackendService
import com.intellij.python.pytools.backend.validateCustomPath
import com.intellij.python.pytools.common.PyToolDependencyGroupDto
import com.intellij.python.pytools.common.PyToolDependencyGroupKind
import com.intellij.python.pytools.common.PyToolSdkDto
import com.intellij.python.pytools.common.PyToolSdkInstallRequest
import com.intellij.python.pytools.common.PyToolSdkOperationResultDto
import com.intellij.python.pytools.common.PyToolSdkRequest
import com.intellij.python.pytools.common.PyToolSdkStateDto
import com.intellij.python.sdk.backend.asItem
import com.intellij.python.sdk.backend.findToolExecutable
import com.intellij.python.sdk.backend.pythonInterpreterAsync
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.pythonSdk

internal class PyToolSdkBackendServiceImpl : PyToolSdkBackendService {
  override suspend fun getStates(project: Project, tool: PyTool<*>): List<PyToolSdkStateDto> =
    projectSdks(project).map { sdk -> sdkState(tool, sdk) }

  override suspend fun getDependencyGroups(project: Project, request: PyToolSdkRequest): List<PyToolDependencyGroupDto> {
    val sdk = requireSdk(project, request.sdk)
    return PythonPackageManager.forSdk(project, sdk).workspaceSupport
      ?.getDependencyGroups(ProjectName(project.name))
      ?.values?.flatten()?.distinct().orEmpty().map { it.toDto() }
  }

  override suspend fun install(
    project: Project,
    tool: PyTool<*>,
    request: PyToolSdkInstallRequest,
  ): PyToolSdkOperationResultDto {
    val sdk = requireSdk(project, request.target.sdk)
    return when (val result = PythonPackageManager.forSdk(project, sdk).installPackages(
      tool.packageName.name,
      dependencyGroup = request.dependencyGroup?.toModel(),
    )) {
      is Result.Success -> PyToolSdkOperationResultDto.Success(sdkState(tool, sdk))
      is Result.Failure -> PyToolSdkOperationResultDto.Failure(result.error.toString())
    }
  }

  private suspend fun sdkState(tool: PyTool<*>, sdk: Sdk): PyToolSdkStateDto {
    val path = sdk.pythonInterpreterAsync().findToolExecutable(tool)
    val version = path?.let {
      when (val result = tool.validateCustomPath(it)) {
        is Result.Success -> result.result.value
        is Result.Failure -> null
      }
    }
    return PyToolSdkStateDto(sdk.toDto(), path?.toString(), version)
  }

  private fun projectSdks(project: Project): List<Sdk> =
    ModuleManager.getInstance(project).modules.mapNotNull { it.pythonSdk }.distinct().sortedBy { it.name }

  private suspend fun Sdk.toDto(): PyToolSdkDto = PyToolSdkDto(name, pythonInterpreterAsync().asItem().shortName)

  private fun requireSdk(project: Project, dto: PyToolSdkDto): Sdk =
    projectSdks(project).firstOrNull { it.name == dto.token } ?: error("Unknown Python SDK: " + dto.token)

  private fun PyDependencyGroup.toDto() = PyToolDependencyGroupDto(
    name,
    when (kind) {
      ModelDependencyGroupKind.DEPENDENCY_GROUP -> PyToolDependencyGroupKind.DEPENDENCY_GROUP
      ModelDependencyGroupKind.OPTIONAL_DEPENDENCY -> PyToolDependencyGroupKind.OPTIONAL_DEPENDENCY
    },
  )

  private fun PyToolDependencyGroupDto.toModel() = PyDependencyGroup(
    name,
    when (kind) {
      PyToolDependencyGroupKind.DEPENDENCY_GROUP -> ModelDependencyGroupKind.DEPENDENCY_GROUP
      PyToolDependencyGroupKind.OPTIONAL_DEPENDENCY -> ModelDependencyGroupKind.OPTIONAL_DEPENDENCY
    },
  )
}
