// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
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
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.python.sdk.backend.findToolExecutable
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.Result
import com.intellij.python.pyproject.model.evolution.pythonInterpreters

internal class PyToolSdkBackendServiceImpl : PyToolSdkBackendService {
  override suspend fun getStates(project: Project, tool: PyTool): List<PyToolSdkStateDto> =
    projectInterpreters(project).map { (interpreter, item) -> interpreterState(tool, interpreter, item) }

  override suspend fun getDependencyGroups(project: Project, request: PyToolSdkRequest): List<PyToolDependencyGroupDto> {
    val (interpreter, _) = requireInterpreter(project, request.sdk)
    return PythonPackageManager.forPythonInterpreter(project, interpreter).workspaceSupport
      ?.getDependencyGroups(ProjectName(project.name))
      ?.values?.flatten()?.distinct().orEmpty().map { it.toDto() }
  }

  override suspend fun install(
    project: Project,
    tool: PyTool,
    request: PyToolSdkInstallRequest,
  ): PyToolSdkOperationResultDto {
    val (interpreter, item) = requireInterpreter(project, request.target.sdk)
    return when (val result = PythonPackageManager.forPythonInterpreter(project, interpreter).installPackages(
      tool.packageName.name,
      dependencyGroup = request.dependencyGroup?.toModel(),
    )) {
      is Result.Success -> PyToolSdkOperationResultDto.Success(interpreterState(tool, interpreter, item))
      is Result.Failure -> PyToolSdkOperationResultDto.Failure(result.error.toString())
    }
  }

  private suspend fun interpreterState(tool: PyTool, interpreter: PythonInterpreter, item: PyInterpreterItem): PyToolSdkStateDto {
    val path = interpreter.findToolExecutable(tool)
    val version = path?.let {
      when (val result = tool.validateCustomPath(it)) {
        is Result.Success -> result.result.value
        is Result.Failure -> null
      }
    }
    return PyToolSdkStateDto(item.toDto(), path?.toString(), version)
  }

  /** Every interpreter of the Python projects with its list item, sorted by name. Builds each item once. */
  private suspend fun projectInterpreters(project: Project): List<Pair<PythonInterpreter, PyInterpreterItem>> =
    project.pythonInterpreters().map { it to it.asItem() }.sortedBy { (_, item) -> item.name }

  /** The token is the interpreter name, as the list shows it. */
  private fun PyInterpreterItem.toDto(): PyToolSdkDto = PyToolSdkDto(name, shortName)

  private suspend fun requireInterpreter(project: Project, dto: PyToolSdkDto): Pair<PythonInterpreter, PyInterpreterItem> =
    projectInterpreters(project).firstOrNull { (_, item) -> item.name == dto.token } ?: error("Unknown Python interpreter: " + dto.token)

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
