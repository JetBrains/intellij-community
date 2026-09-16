// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.impl.frontend

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.project.projectId
import com.intellij.python.pytools.common.PyToolApi
import com.intellij.python.pytools.common.PyToolEnabledStateDto
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.pytools.common.PyToolRequest
import com.intellij.python.pytools.common.PyToolSetEnabledRequest
import com.intellij.python.pytools.frontend.PyToolFrontend
import com.intellij.python.pytools.frontend.PyToolsFrontendState
import com.intellij.python.lsp.core.frontend.LspPyToolFrontend
import com.intellij.python.pytools.frontend.ui.configuration.PyExternalToolsConfigurable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

private const val NOTIFICATION_GROUP_ID = "Python LSP Tools"
private const val DONT_ASK_PROPERTY_PREFIX = "python.lsp.tool.dont.ask."

private val ADVERTISED_TOOL_IDS = listOf("ruff", "basedpyright", "pyright", "ty").map(::PyToolId)

private fun lspTools(): List<LspPyToolFrontend> = ADVERTISED_TOOL_IDS
  .mapNotNull(PyToolFrontend::findById)
  .filterIsInstance<LspPyToolFrontend>()

/**
 * Service that detects LSP tools installed in the user's virtual environment
 * and shows a balloon notification offering to enable them.
 *
 * Also listens for package installation events to suggest enabling tools when installed.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class PyLspToolAdvertiserService(private val project: Project, private val cs: CoroutineScope) {

  private val shownForTools = mutableSetOf<String>()

  /**
   * Checks for installed LSP tools and shows notifications for any that are found
   * but not yet enabled.
   */
  @Suppress("unused")
  suspend fun checkAndAdvertise() {
    val tools = lspTools()
    val api = PyToolApi.getInstance()
    val installedTools = tools.filterTo(mutableSetOf()) { tool ->
      api.getSdkStates(PyToolRequest(project.projectId(), tool.toolId)).any { it.path != null }
    }
    val pyToolsState = PyToolsFrontendState.getInstance(project)

    val toolsToAdvertise = tools.mapNotNull { tool ->
      val isInstalled = tool in installedTools
      val name = tool.presentableName
      val isEnabled = pyToolsState.isEnabled(tool.toolId)
      if (isInstalled && (isEnabled || isDontAskSet(tool))) {
        thisLogger().debug("LSP tool '$name': installed=true, enabled=$isEnabled, dontAsk=${isDontAskSet(tool)}")
      }
      // shownForTools (keyed on the stable presentable name) suppresses repeat notifications when
      // checkAndAdvertise re-runs on package-change events.
      if (!isInstalled || isEnabled || isDontAskSet(tool) || name in shownForTools) {
        return@mapNotNull null
      }
      tool
    }

    if (toolsToAdvertise.size > 1) {
      for (tool in toolsToAdvertise) {
        thisLogger().info("LSP tool '${tool.presentableName}' installed but not enabled")
        shownForTools.add(tool.presentableName)
      }
      showMultipleToolsNotification(toolsToAdvertise)
    }
    else if (toolsToAdvertise.size == 1) {
      val tool = toolsToAdvertise.single()
      thisLogger().info("LSP tool '${tool.presentableName}' installed but not enabled, showing notification")
      shownForTools.add(tool.presentableName)
      showNotification(tool)
    }
  }

  private fun showMultipleToolsNotification(tools: List<LspPyToolFrontend>) {
    val toolNames = tools.dropLast(1).joinToString { it.presentableName } +
                    " and " + tools.last().presentableName
    thisLogger().info("Notifying user about multiple LSP tools: $toolNames")
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(
        PyLspImplFrontendBundle.message("lsp.tool.advertiser.multiple.title"),
        PyLspImplFrontendBundle.message("lsp.tool.advertiser.multiple.message", toolNames),
        NotificationType.INFORMATION
      )
      .addAction(NotificationAction.createSimpleExpiring(PyLspImplFrontendBundle.message("lsp.tool.advertiser.settings")) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, PyExternalToolsConfigurable::class.java)
      })
      .addAction(NotificationAction.createSimpleExpiring(PyLspImplFrontendBundle.message("lsp.tool.advertiser.ignore")) {
        for (tool in tools) {
          setDontAsk(tool)
        }
      })

    notification.notify(project)
  }

  private fun showNotification(tool: LspPyToolFrontend) {
    val name = tool.presentableName
    thisLogger().info("Notifying user about LSP tool: $name")
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(
        PyLspImplFrontendBundle.message("lsp.tool.advertiser.title", name),
        PyLspImplFrontendBundle.message("lsp.tool.advertiser.message", name),
        NotificationType.INFORMATION
      )
      .setIcon(tool.icon)
      .addAction(NotificationAction.createSimpleExpiring(PyLspImplFrontendBundle.message("lsp.tool.advertiser.yes")) {
        // Mirror the External Tools page's "Enable" toggle: flip the framework-level state and
        // run the tool's lifecycle hook (which starts the LSP server).
        cs.launch {
          val state = PyToolApi.getInstance().setEnabled(
            PyToolSetEnabledRequest(PyToolRequest(project.projectId(), tool.toolId), true),
          )
          PyToolsFrontendState.getInstance(project).apply(
            PyToolEnabledStateDto(state.toolId, state.enabled),
          )
        }
      })
      .addAction(NotificationAction.createSimpleExpiring(PyLspImplFrontendBundle.message("lsp.tool.advertiser.no")) {
        setDontAsk(tool)
      })
      .addAction(NotificationAction.createSimpleExpiring(PyLspImplFrontendBundle.message("lsp.tool.advertiser.settings")) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, PyExternalToolsConfigurable::class.java)
      })

    notification.notify(project)
  }

  private fun isDontAskSet(tool: PyToolFrontend): Boolean {
    return PropertiesComponent.getInstance(project).getBoolean(DONT_ASK_PROPERTY_PREFIX + tool.presentableName, false)
  }

  private fun setDontAsk(tool: PyToolFrontend) {
    val name = tool.presentableName
    thisLogger().debug("Setting don't-ask for LSP tool: $name")
    PropertiesComponent.getInstance(project).setValue(DONT_ASK_PROPERTY_PREFIX + name, true)
  }

  companion object {
    fun getInstance(project: Project): PyLspToolAdvertiserService =
      project.service<PyLspToolAdvertiserService>()
  }
}


class PyLspToolAdvertiserStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    return
    //thisLogger().debug("PyLspToolAdvertiserStartupActivity: waiting 15s before first check")
    //delay(15.seconds)
    //PyLspToolAdvertiserService.getInstance(project).checkAndAdvertise()
  }
}
