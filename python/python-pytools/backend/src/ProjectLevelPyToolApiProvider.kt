// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.python.pytools.common.ProjectLevelPyToolApi
import com.intellij.python.pytools.common.PyToolConfigurationDto
import com.intellij.python.pytools.common.PyToolEnabledStateDto
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.pytools.common.PyToolRequest
import com.intellij.python.pytools.common.PyToolSetConfigurationRequest
import com.intellij.python.pytools.common.PyToolSetEnabledRequest
import com.intellij.python.pytools.common.PyToolStateDto
import fleet.rpc.remoteApiDescriptor

internal class ProjectLevelPyToolApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ProjectLevelPyToolApi>()) { ProjectLevelPyToolApiImpl }
  }
}

private object ProjectLevelPyToolApiImpl : ProjectLevelPyToolApi {
  override suspend fun isStateInitialized(projectId: ProjectId): Boolean =
    PyToolsState.getInstance(projectId.findProject()).isInitialized()

  override suspend fun initializeState(projectId: ProjectId) {
    val project = projectId.findProject()
    val entries = ProjectLevelPyTool.extensions
      .map { PyToolEnabledStateDto(PyToolId(it.fusId), it.migrateLegacyState(project).enabled) }
      .toList()
    PyToolsState.getInstance(project).initialize(entries)
  }

  override suspend fun observeEnabledStates(projectId: ProjectId) =
    PyToolsState.getInstance(projectId.findProject()).enabledStates()

  override suspend fun setEnabled(request: PyToolSetEnabledRequest): PyToolStateDto {
    val project = request.tool.projectId.findProject()
    val tool = projectLevelTool(request.tool) ?: error("Not a project-level Python tool: " + request.tool.toolId.value)
    tool.setEnabledOn(project, request.enabled)
    return buildToolState(project, tool)
  }

  override suspend fun getConfiguration(request: PyToolRequest): PyToolConfigurationDto? =
    projectLevelTool(request)?.configurationState(request.projectId.findProject())

  override suspend fun setConfiguration(request: PyToolSetConfigurationRequest) {
    val tool = projectLevelTool(request.tool) ?: return
    tool.applyConfigurationStateIfCompatible(request.tool.projectId.findProject(), request.configuration)
  }
}

/**
 * The one this request names, or `null` when the id names a tool without per-project state (a package
 * manager) or no tool at all.
 *
 * Matches on [PyExecutable.fusId] — the id the backend puts in every [PyToolId] it sends, and the one
 * [PyTool.findExecutable] resolves the same request by.
 */
private fun projectLevelTool(request: PyToolRequest): ProjectLevelPyTool<*>? =
  ProjectLevelPyTool.extensions.firstOrNull { it.fusId == request.toolId.value }

/**
 * Applies [state] only when it is the DTO type this tool declares.
 *
 * The page sends back what it was given, so a mismatch means a bug or a frontend newer than the
 * backend (a [PyToolConfigurationDto] the serializer could not resolve); either way the tool never sees it.
 */
private fun <C : PyToolConfigurationDto> ProjectLevelPyTool<C>.applyConfigurationStateIfCompatible(
  project: Project,
  state: PyToolConfigurationDto,
) {
  val configurationClass = configurationState(project).javaClass
  if (!configurationClass.isInstance(state)) return
  applyConfigurationState(project, configurationClass.cast(state))
}
