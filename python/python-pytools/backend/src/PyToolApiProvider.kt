// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.project.findProject
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.python.pytools.common.PyToolApi
import com.intellij.python.pytools.common.PyToolConfigurationDto
import com.intellij.python.pytools.common.PyToolDescriptorDto
import com.intellij.python.pytools.common.PyToolEnabledStateDto
import com.intellij.python.pytools.common.PyToolEventKind
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.pytools.common.PyToolLogEventRequest
import com.intellij.python.pytools.common.PyToolSetEnabledRequest
import com.intellij.python.pytools.common.PyToolSetConfigurationRequest
import com.intellij.python.pytools.common.PyToolOperationResultDto
import com.intellij.python.pytools.common.PyToolPathDto
import com.intellij.python.pytools.common.PyToolPathKind
import com.intellij.python.pytools.common.PyToolPathStateDto
import com.intellij.python.pytools.common.PyToolPathRequest
import com.intellij.python.pytools.common.PyToolRequest
import com.intellij.python.pytools.common.PyToolSdkInstallRequest
import com.intellij.python.pytools.common.PyToolSdkOperationResultDto
import com.intellij.python.pytools.common.PyToolSdkRequest
import com.intellij.python.pytools.common.PyToolSdkStateDto
import com.intellij.python.pytools.common.PyToolSetPathRequest
import com.intellij.python.pytools.common.PyToolStateDto
import com.intellij.python.pytools.common.PyToolValidationDto
import com.intellij.python.pytools.common.PyToolsRequest
import com.intellij.python.pytools.backend.statistics.PyToolUsagesCollector
import com.intellij.python.requirements.PyPackageVersionComparator
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path

internal class PyToolApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<PyToolApi>()) { PyToolApiImpl }
  }
}

private object PyToolApiImpl : PyToolApi {
  override suspend fun isStateInitialized(projectId: ProjectId): Boolean =
    PyToolsState.getInstance(projectId.findProject()).isInitialized()

  override suspend fun initializeState(projectId: ProjectId) {
    val project = projectId.findProject()
    val entries = PyTool.EP_NAME.extensionList.mapNotNull { tool ->
      tool.migrateLegacyState(project)?.let { PyToolEnabledStateDto(PyToolId(tool.packageName.name), it.enabled) }
    }
    PyToolsState.getInstance(project).initialize(entries)
  }

  override suspend fun observeEnabledStates(projectId: ProjectId) =
    PyToolsState.getInstance(projectId.findProject()).enabledStates()

  override suspend fun getConfiguration(request: PyToolRequest): PyToolConfigurationDto? {
    val project = request.projectId.findProject()
    return requireTool(request).configurationState(project)
  }

  override suspend fun getStates(request: PyToolsRequest): List<PyToolStateDto> {
    val project = request.projectId.findProject()
    val eel = project.getEelDescriptor().toEelApi()
    val installed = PyToolProbeCache.getInstance().listing(eel)
    val executables = request.toolIds.mapNotNull { id -> PyTool.findExecutable(id.value) }
    // One coroutine per tool: a state resolves a path and, for a path the manager does not know, runs
    // `<path> --version`. In a sequence every tool waits for the ones before it, so the pages showed no
    // version until the slowest tool had answered.
    return coroutineScope {
      executables.map { executable ->
        async { state(project, executable, installed = (executable as? PyTool<*>)?.let { installed[it] }) }
      }.awaitAll()
    }
  }

  override suspend fun getPaths(request: PyToolsRequest): List<PyToolPathStateDto> {
    val project = request.projectId.findProject()
    val descriptor = project.getEelDescriptor()
    val executables = request.toolIds.mapNotNull { id -> PyTool.findExecutable(id.value) }
    // One coroutine per tool: a cache hit returns at once, and a cold cache detects every tool in parallel.
    return coroutineScope {
      executables.map { executable ->
        async {
          val custom = executable.getCustomExecutablePath(descriptor)
          val path = custom ?: PyExecutableCache.getInstance().get(descriptor, executable)
          PyToolPathStateDto(
            toolId = PyToolId(executable.fusId),
            path = when {
              custom != null -> PyToolPathDto(custom.toString(), PyToolPathKind.CUSTOM)
              path != null -> PyToolPathDto(path.toString(), PyToolPathKind.DETECTED)
              else -> null
            },
          )
        }
      }.awaitAll()
    }
  }

  override suspend fun getVersion(request: PyToolRequest): String? {
    val project = request.projectId.findProject()
    val descriptor = project.getEelDescriptor()
    val executable = PyTool.findExecutable(request.toolId.value) ?: return null
    val path = executable.getCustomExecutablePath(descriptor)
               ?: PyExecutableCache.getInstance().get(descriptor, executable)
               ?: return null
    return resolveVersion(project, executable, path)
  }

  /**
   * The version of [executable] at [path]: free from the manager listing when it covers that exact file, and a
   * cached `<path> --version` run otherwise.
   */
  private suspend fun resolveVersion(project: Project, executable: PyExecutable, path: Path): String? {
    val tool = executable as? PyTool<*> ?: return null
    val descriptor = project.getEelDescriptor()
    val managed = PyToolProbeCache.getInstance().listing(descriptor.toEelApi())[tool]
      ?.takeIf { it.path.normalize() == path.normalize() }
    return managed?.installedVersion ?: PyToolProbeCache.getInstance().version(descriptor, tool, path)?.value
  }

  override suspend fun validatePath(request: PyToolPathRequest): PyToolValidationDto {
    val project = request.tool.projectId.findProject()
    val path = EelPath.parse(request.path, project.getEelDescriptor()).asNioPath()
    return when (val result = requireTool(request.tool).validateCustomPath(path)) {
      is Result.Success -> PyToolValidationDto.Valid(result.result.value)
      is Result.Failure -> PyToolValidationDto.Invalid(result.error.toString())
    }
  }

  override suspend fun setPath(request: PyToolSetPathRequest): PyToolStateDto {
    val project = request.tool.projectId.findProject()
    val tool = requireTool(request.tool)
    val descriptor = project.getEelDescriptor()
    val path = request.path?.let { EelPath.parse(it, descriptor).asNioPath() }
    tool.setCustomExecutablePath(descriptor, path)
    return state(project, tool)
  }

  override suspend fun setEnabled(request: PyToolSetEnabledRequest): PyToolStateDto {
    val project = request.tool.projectId.findProject()
    val tool = requireTool(request.tool)
    PyToolsState.getInstance(project).setEnabled(request.tool.toolId, request.enabled)
    tool.onEnabledChanged(project, request.enabled)
    return state(project, tool)
  }

  override suspend fun setConfiguration(request: PyToolSetConfigurationRequest): PyToolStateDto {
    val project = request.tool.projectId.findProject()
    val tool = requireTool(request.tool)
    tool.applyConfigurationStateIfCompatible(project, request.configuration)
    return state(project, tool)
  }

  override suspend fun install(request: PyToolRequest): PyToolOperationResultDto =
    operate(request) { project, tool -> tool.performToolInstallation(project.getEelDescriptor().toEelApi()) }

  override suspend fun upgrade(request: PyToolRequest): PyToolOperationResultDto =
    operate(request) { project, tool -> tool.performToolUpgrade(project.getEelDescriptor().toEelApi()) }

  override suspend fun getSdkStates(request: PyToolRequest): List<PyToolSdkStateDto> {
    val project = request.projectId.findProject()
    return PyToolSdkBackendService.getInstance().getStates(project, requireTool(request))
  }

  override suspend fun getDependencyGroups(request: PyToolSdkRequest) =
    PyToolSdkBackendService.getInstance().getDependencyGroups(request.tool.projectId.findProject(), request)

  override suspend fun installIntoSdk(request: PyToolSdkInstallRequest): PyToolSdkOperationResultDto {
    val project = request.target.tool.projectId.findProject()
    return PyToolSdkBackendService.getInstance().install(project, requireTool(request.target.tool), request)
  }

  override suspend fun logEvent(request: PyToolLogEventRequest) {
    val project = request.tool.projectId.findProject()
    val tool = requireTool(request.tool)
    when (request.event) {
      PyToolEventKind.CONFIGURATION_CHANGED -> PyToolUsagesCollector.Helper.logConfigurationChanged(project, tool, request.source)
      PyToolEventKind.INSTALLED -> PyToolUsagesCollector.Helper.logToolInstalled(project, tool, request.source)
      PyToolEventKind.UPDATED -> PyToolUsagesCollector.Helper.logToolUpdated(project, tool, request.source)
    }
  }

  private suspend fun operate(
    request: PyToolRequest,
    operation: suspend (Project, PyTool<*>) -> PyResult<Path>,
  ): PyToolOperationResultDto {
    val project = request.projectId.findProject()
    val tool = requireTool(request)
    return when (val result = operation(project, tool)) {
      is Result.Success -> {
        val path = result.result
        val dto = state(project, tool, knownPath = path)
        // The user just asked for this install or upgrade, so resolving the new version here is worth a process.
        PyToolOperationResultDto.Success(dto.takeIf { it.version != null } ?: dto.copy(version = resolveVersion(project, tool, path)))
      }
      is Result.Failure -> PyToolOperationResultDto.Failure(result.error.toString())
    }
  }

  private suspend fun state(
    project: Project,
    executable: PyExecutable,
    knownPath: Path? = null,
    installed: InstalledInfo? = null,
  ): PyToolStateDto {
    val descriptor = project.getEelDescriptor()
    val custom = executable.getCustomExecutablePath(descriptor)
    val path = custom ?: knownPath ?: PyExecutableCache.getInstance().get(descriptor, executable)
    // What the manager reports counts only when the path resolved above is the very file it installed. Another
    // path can hold a different build of the tool, or another program altogether, and an upgrade through the
    // manager would not touch it. A path the manager reports through a symlink compares unequal here, which
    // costs a version probe but never reports a version or an upgrade of a different file.
    val managed = installed?.takeIf { path != null && it.path.normalize() == path.normalize() }
    val details = when (executable) {
      is PyTool<*> -> PyToolDetails(
        // Only the version the manager already knows. Running `<path> --version` for every tool of a page
        // costs a process per tool, so a version the manager cannot supply is asked for through getVersion,
        // where the page shows it.
        version = managed?.installedVersion,
        minimumSupportedVersion = executable.minimumSupportedVersion?.toCompactString(),
        canInstall = executable.manager?.canInstall(descriptor) == true,
        configuration = executable.configurationState(project),
        selectedAsTypeEngine = executable.isSelectedAsTypeEngine(project),
      )
      else -> PyToolDetails()
    }
    return PyToolStateDto(
      toolId = PyToolId(executable.fusId),
      descriptor = PyToolDescriptorDto(details.minimumSupportedVersion),
      enabled = PyToolsState.getInstance(project).isEnabled(PyToolId(executable.fusId)),
      path = when {
        custom != null -> PyToolPathDto(custom.toString(), PyToolPathKind.CUSTOM)
        path != null -> PyToolPathDto(path.toString(), PyToolPathKind.DETECTED)
        else -> null
      },
      version = details.version,
      canInstall = details.canInstall,
      // `uv tool list --outdated` repeats the installed version when a tool is up to date; report an
      // upgrade only when the latest one is actually newer.
      latestVersion = managed?.latestVersion?.takeIf {
        PyPackageVersionComparator.STR_COMPARATOR.compare(it, managed.installedVersion) > 0
      },
      configuration = details.configuration,
      selectedAsTypeEngine = details.selectedAsTypeEngine,
    )
  }

  private fun requireTool(request: PyToolRequest): PyTool<*> =
    PyTool.findByPackageName(request.toolId.value) ?: error("Unknown Python tool: " + request.toolId.value)
}

private fun <C : PyToolConfigurationDto> PyTool<C>.applyConfigurationStateIfCompatible(
  project: Project,
  state: PyToolConfigurationDto,
) {
  val currentState = configurationState(project) ?: return
  if (!currentState.javaClass.isInstance(state)) return
  applyConfigurationState(project, currentState.javaClass.cast(state))
}

private data class PyToolDetails(
  val version: String? = null,
  val minimumSupportedVersion: String? = null,
  val canInstall: Boolean = false,
  val configuration: PyToolConfigurationDto? = null,
  val selectedAsTypeEngine: Boolean = false,
)
