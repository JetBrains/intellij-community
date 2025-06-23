package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clientConfiguration.McpClient
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.settings.McpServerSettingsConfigurable
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.*
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry

@Service(Service.Level.PROJECT)
@State(name = "McpServerSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)],)
internal class McpClientDetectionSettings : SimplePersistentStateComponent<McpClientDetectionSettings.MyState>(MyState()) {
  internal class MyState : BaseState() {
    var doNotShowServerDisabledAgain: Boolean by property(false)
    var doNotShowUnconfiguredAgain: Boolean by property(false)
    var processedClients: MutableSet<String> by stringSet()
  }
}

internal class McpClientDetectionActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (Registry.`is`("mcp.server.detect.mcp.clients")) {
      val mcpClientDetectionSettings = project.service<McpClientDetectionSettings>()

      val detectedClients = McpClientDetector.detectMcpClients(project)
      if (McpServerSettings.getInstance().state.enableMcpServer) {
        showUnconfiguredNotificationIfNeeded(mcpClientDetectionSettings, detectedClients, project)
        return
      }

      val doNotShowServerDisabled = mcpClientDetectionSettings.state.doNotShowServerDisabledAgain
      if (doNotShowServerDisabled) return

      if (detectedClients.isNotEmpty()) {
        showMcpServerEnablingSuggestionNotification(project, detectedClients)
        showUnconfiguredNotificationIfNeeded(mcpClientDetectionSettings, detectedClients, project)
      }
    }
  }

  private fun showUnconfiguredNotificationIfNeeded(
    mcpClientDetectionSettings: McpClientDetectionSettings,
    detectedClients: List<McpClient>,
    project: Project,
  ) {
    if (mcpClientDetectionSettings.state.doNotShowUnconfiguredAgain) return
    val unconfiguredClients = detectedClients.filter { !it.isConfigured() }
    if (unconfiguredClients.isNotEmpty()) {
      showMcpServerAutomaticConfigurationNotification(project, unconfiguredClients)
    }
  }



  private fun showMcpServerAutomaticConfigurationNotification(project: Project, unconfiguredClients: List<McpClient>) {
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("MCP Server")
      .createNotification(
        McpServerBundle.message("mcp.unconfigured.clients.detected.notification.title"),
        McpServerBundle.message("mcp.unconfigured.clients.detected.notification.message", unconfiguredClients.joinToString(", ") { it.name }),
        NotificationType.INFORMATION
      )
    notification
      .addAction(object : AnAction(McpServerBundle.message("mcp.unconfigured.clients.detected.configure.json")) {
        override fun actionPerformed(e: AnActionEvent) {
          unconfiguredClients.forEach { it.configure() }
          notification.expire()
        }
      })
      .addAction(object : AnAction(McpServerBundle.message("mcp.unconfigured.clients.detected.configure.settings.json")) {
        override fun actionPerformed(e: AnActionEvent) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, McpServerSettingsConfigurable::class.java)
        }
      })
      .addAction(object : AnAction(McpServerBundle.message("mcp.clients.detected.action.dont.show")) {
        override fun actionPerformed(e: AnActionEvent) {
          project.service<McpClientDetectionSettings>().state.doNotShowUnconfiguredAgain = true
          notification.expire()
        }
    }).notify(project)
  }

  private fun showMcpServerEnablingSuggestionNotification(project: Project, detectedClients: List<McpClient>) {

    val currentProcessedClients = project.service<McpClientDetectionSettings>().state.processedClients
    val newProcessedClients = (currentProcessedClients + detectedClients.map { it.name }).toMutableSet()
    if (currentProcessedClients != newProcessedClients) {
      project.service<McpClientDetectionSettings>().state.processedClients = newProcessedClients
      project.service<McpClientDetectionSettings>().state.intIncrementModificationCount()
    }
    else {
      return
    }

    val clientNames = detectedClients.joinToString(", ") { it.name }
    NotificationGroupManager.getInstance()
      .getNotificationGroup("MCP Server")
      .createNotification(
        McpServerBundle.message("mcp.clients.detected.notification.title"),
        McpServerBundle.message("mcp.clients.detected.notification.message", clientNames, ApplicationNamesInfo.getInstance().fullProductName),
        NotificationType.INFORMATION
      )
      .addAction(object : AnAction(McpServerBundle.message("mcp.clients.detected.action.enable")) {
        override fun actionPerformed(e: AnActionEvent) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, McpServerSettingsConfigurable::class.java)
        }
      })
      .addAction(object : AnAction(McpServerBundle.message("mcp.clients.detected.action.dont.show")) {
        override fun actionPerformed(e: AnActionEvent) {
          project.service<McpClientDetectionSettings>().state.doNotShowServerDisabledAgain = true
        }
      })
      .notify(project)
  }

}