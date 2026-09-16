// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.frontend.ui.configuration

import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Version
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.project.projectId
import com.intellij.python.pytools.common.PyToolApi
import com.intellij.python.pytools.common.PyToolDependencyGroupDto
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.pytools.common.PyToolOperationResultDto
import com.intellij.python.pytools.common.PyToolRequest
import com.intellij.python.pytools.common.PyToolSdkDto
import com.intellij.python.pytools.common.PyToolSdkInstallRequest
import com.intellij.python.pytools.common.PyToolSdkOperationResultDto
import com.intellij.python.pytools.common.PyToolSdkRequest
import com.intellij.python.pytools.common.PyToolStateDto
import com.intellij.python.pytools.common.PyToolsRequest
import com.intellij.python.pytools.common.PyToolActionSource
import com.intellij.python.pytools.common.PyToolEventKind
import com.intellij.python.pytools.common.PyToolLogEventRequest
import com.intellij.python.pytools.frontend.ui.PyToolsUiBundle
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/** Executes Python-tool operations through the backend RPC API and keeps frontend row state current. */
internal class PyToolManagementController(
  private val project: Project,
  private val onStateChanged: () -> Unit,
  private val refreshRow: (ToolRow) -> Unit,
) {
  private var scope: CoroutineScope? = null

  val uvAvailable: AtomicProperty<Boolean?> = AtomicProperty(null)

  fun isUpgradeAvailable(toolRow: ToolRow): Boolean = toolRow.latestVersion != null

  fun latestVersionFor(toolRow: ToolRow): Version? = toolRow.latestVersion

  fun onShown(scope: CoroutineScope) {
    this.scope = scope
    scope.launch {
      refreshUvAvailability()
    }
  }

  fun installTool(toolRow: ToolRow, source: PyToolActionSource) {
    runToolAction(
      toolRow,
      progressTitleKey = "settings.external.tools.install.progress",
      errorTitleKey = "settings.external.tools.install.error.title",
    ) {
      PyToolApi.getInstance().install(toolRow.request())
    } ?: return
    logEvent(toolRow, source, PyToolEventKind.INSTALLED)
    toolRow.lastSuccessMessage = toolRow.version?.let {
      PyToolsUiBundle.message("settings.external.tools.install.success.to.version.balloon", toolRow.toolIdValue(), it)
    } ?: PyToolsUiBundle.message("settings.external.tools.install.success.balloon", toolRow.toolIdValue())
  }

  fun upgradeTool(toolRow: ToolRow, source: PyToolActionSource) {
    val previousVersion = toolRow.version
    runToolAction(
      toolRow,
      progressTitleKey = "settings.external.tools.upgrade.progress",
      errorTitleKey = "settings.external.tools.upgrade.error.title",
    ) {
      PyToolApi.getInstance().upgrade(toolRow.request())
    } ?: return
    logEvent(toolRow, source, PyToolEventKind.UPDATED)
    toolRow.lastSuccessMessage = upgradeFeedbackMessage(toolRow, previousVersion, toolRow.version)
  }

  fun installIntoSdkChoosingGroup(
    toolRow: ToolRow,
    sdk: PyToolSdkDto,
    anchor: JComponent,
    source: PyToolActionSource,
  ) {
    val activeScope = checkNotNull(scope) { "The tools panel is not shown" }
    activeScope.launch {
      val groups = PyToolApi.getInstance().getDependencyGroups(PyToolSdkRequest(toolRow.request(), sdk))
      withContext(Dispatchers.EDT) {
        if (groups.isEmpty()) {
          installIntoSdk(toolRow, sdk, source)
          return@withContext
        }
        JBPopupFactory.getInstance()
          .createPopupChooserBuilder(groups)
          .setTitle(PyToolsUiBundle.message("settings.external.tools.install.group.chooser.title"))
          .setRenderer(listCellRenderer {
            @NlsSafe val groupName = value.name
            text(groupName) { align = LcrInitParams.Align.RIGHT }
          })
          .setItemChosenCallback { group ->
            activeScope.launch {
              installIntoSdk(toolRow, sdk, source, group)
            }
          }
          .createPopup()
          .showUnderneathOf(anchor)
      }
    }
  }

  private suspend fun installIntoSdk(
    toolRow: ToolRow,
    sdk: PyToolSdkDto,
    source: PyToolActionSource,
    dependencyGroup: PyToolDependencyGroupDto? = null,
  ) {
    val title = PyToolsUiBundle.message("settings.external.tools.install.progress", toolRow.toolIdValue())
    val errorTitle = PyToolsUiBundle.message("settings.external.tools.install.error.title", toolRow.toolIdValue())
    val result = withModalProgress(project, title) {
      PyToolApi.getInstance().installIntoSdk(
        PyToolSdkInstallRequest(PyToolSdkRequest(toolRow.request(), sdk), dependencyGroup),
      )
    }
    when (result) {
      is PyToolSdkOperationResultDto.Success -> Unit
      is PyToolSdkOperationResultDto.Failure -> {
        @NlsSafe val errorMessage = result.message
        withContext(Dispatchers.EDT) {
          Messages.showErrorDialog(project, errorMessage, errorTitle)
        }
        return
      }
    }
    val sdkAvailability = withContext(Dispatchers.Default) {
      PyToolApi.getInstance().logEvent(PyToolLogEventRequest(toolRow.request(), source, PyToolEventKind.INSTALLED))
      SdkAvailability(PyToolApi.getInstance().getSdkStates(toolRow.request()))
    }
    withContext(Dispatchers.EDT) {
      toolRow.sdkAvailability = sdkAvailability
      refreshRow(toolRow)
    }
  }

  @Suppress("DialogTitleCapitalization")
  fun installUv() {
    val title = PyToolsUiBundle.message("settings.external.tools.install.uv.progress")
    val errorTitle = PyToolsUiBundle.message("settings.external.tools.install.uv.error.title")
    val result = runWithModalProgressBlocking(project, title) {
      PyToolApi.getInstance().install(PyToolRequest(project.projectId(), PyToolId("uv")))
    }
    when (result) {
      is PyToolOperationResultDto.Success -> Unit
      is PyToolOperationResultDto.Failure -> {
        @NlsSafe val errorMessage = result.message
        Messages.showErrorDialog(project, errorMessage, errorTitle)
        return
      }
    }
    uvAvailable.set(true)
    onStateChanged()
    scope?.launch { refreshUvAvailability() }
  }

  private fun runToolAction(
    toolRow: ToolRow,
    progressTitleKey: String,
    errorTitleKey: String,
    action: suspend () -> PyToolOperationResultDto,
  ): PyToolStateDto? {
    val activeScope = checkNotNull(scope) { "The tools panel is not shown" }
    val title = PyToolsUiBundle.message(progressTitleKey, toolRow.toolIdValue())
    val errorTitle = PyToolsUiBundle.message(errorTitleKey, toolRow.toolIdValue())
    toolRow.actionInProgress = true
    refreshRow(toolRow)
    val result = try {
      runWithModalProgressBlocking(project, title) { action() }
    }
    catch (e: CancellationException) {
      toolRow.actionInProgress = false
      refreshRow(toolRow)
      throw e
    }
    toolRow.actionInProgress = false
    val state = handleResult(toolRow, result, errorTitle) ?: return null
    refreshRow(toolRow)
    activeScope.launch {
      refreshToolState(toolRow)
      refreshUvAvailability()
    }
    return state
  }

  private fun handleResult(toolRow: ToolRow, result: PyToolOperationResultDto, errorTitle: @Nls String): PyToolStateDto? =
    when (result) {
      is PyToolOperationResultDto.Success -> {
        toolRow.applyBackendState(result.state)
        result.state
      }
      is PyToolOperationResultDto.Failure -> {
        @NlsSafe val errorMessage = result.message
        Messages.showErrorDialog(project, errorMessage, errorTitle)
        null
      }
    }

  /**
   * Whether uvx resolves to an executable. This asks for the path alone: a full state also runs the
   * tool manager and a `--version` process, which the pages already pay for once through their own
   * state call.
   */
  private suspend fun refreshUvAvailability() {
    val state = PyToolApi.getInstance().getPaths(
      PyToolsRequest(project.projectId(), listOf(PyToolId("uvx"))),
    ).singleOrNull()
    uvAvailable.set(state?.path != null)
    onStateChanged()
  }

  private suspend fun refreshToolState(toolRow: ToolRow) {
    val state = PyToolApi.getInstance().getStates(
      PyToolsRequest(project.projectId(), listOf(toolRow.tool.toolId)),
    ).singleOrNull() ?: return
    toolRow.applyBackendState(state)
    refreshRow(toolRow)
    onStateChanged()
  }

  private fun upgradeFeedbackMessage(toolRow: ToolRow, previous: Version?, current: Version?): String {
    val name = toolRow.toolIdValue()
    return when {
      current != null && previous == current ->
        PyToolsUiBundle.message("settings.external.tools.upgrade.up.to.date.balloon", name, current)
      current != null ->
        PyToolsUiBundle.message("settings.external.tools.upgrade.success.to.version.balloon", name, current)
      else -> PyToolsUiBundle.message("settings.external.tools.upgrade.success.balloon", name)
    }
  }

  private fun ToolRow.request(): PyToolRequest = PyToolRequest(project.projectId(), tool.toolId)

  private fun logEvent(toolRow: ToolRow, source: PyToolActionSource, event: PyToolEventKind) {
    runWithModalProgressBlocking(project, toolRow.tool.presentableName) {
      PyToolApi.getInstance().logEvent(PyToolLogEventRequest(toolRow.request(), source, event))
    }
  }

  private fun ToolRow.toolIdValue(): String = tool.toolId.value
}
