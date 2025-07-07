package com.intellij.mcpserver.impl

import com.intellij.ide.BrowserUtil
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clientConfiguration.McpClient
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.settings.McpServerSettingsConfigurable
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.application

@Service(Service.Level.APP)
@State(name = "McpNotificationSettings", storages = [Storage("mcpNotification.xml", roamingType = RoamingType.DISABLED)])
internal class McpClientDetectionSettings : SimplePersistentStateComponent<McpClientDetectionSettings.MyState>(MyState()) {
  internal class MyState : BaseState() {
    var doNotShowServerDisabledAgain: Boolean by property(false)
    var processedClients: MutableSet<String> by stringSet()
  }
}

internal class McpClientDetectionActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (Registry.`is`("mcp.server.detect.mcp.clients")) {
      val mcpClientDetectionSettings = application.service<McpClientDetectionSettings>()

      val detectedClients = McpClientDetector.detectMcpClients(project)
      if (McpServerSettings.getInstance().state.enableMcpServer) {
        showUnconfiguredNotificationIfNeeded(detectedClients, project)
        suggestToChangePortIfNeeded(detectedClients, project)
        return
      }

      val doNotShowServerDisabled = mcpClientDetectionSettings.state.doNotShowServerDisabledAgain
      if (doNotShowServerDisabled) return

      if (detectedClients.isNotEmpty()) {
        showMcpServerEnablingSuggestionNotification(project, detectedClients)
      }
    }
  }

  private fun suggestToChangePortIfNeeded(
    detectedClients: List<McpClient>,
    project: Project,
  ) {
    val notMatchingPort = detectedClients.filter { it.isConfigured() ?: false }.filterNot { it.isPortCorrect() }
    if (notMatchingPort.isNotEmpty()) {
      val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("MCP Server")
        .createNotification(
          McpServerBundle.message("mcp.clients.with.wrong.port.detected.notification.title"),
          McpServerBundle.message("mcp.clients.with.wrong.port.detected.notification.message", notMatchingPort.joinToString(", ") { it.name.displayName }),
          NotificationType.INFORMATION
        )
        .setSuggestionType(true)
        .setImportant(false)
      notification.setSuppressShowingPopup(true)
      notification
        .addAction(AutoconfigureAction(project, notMatchingPort, notification))
        .addAction(ShowSettingsAction(project)).notify(project)

    }

  }

  private fun showUnconfiguredNotificationIfNeeded(
    detectedClients: List<McpClient>,
    project: Project,
  ) {
    val currentProcessedClients = application.service<McpClientDetectionSettings>().state.processedClients.toMutableSet()
    val newProcessedClients = (currentProcessedClients + detectedClients.map { it.name.displayName }).toMutableSet()

    if (currentProcessedClients != newProcessedClients) {
      application.service<McpClientDetectionSettings>().state.processedClients = newProcessedClients
      application.service<McpClientDetectionSettings>().state.intIncrementModificationCount()

      val newClients = newProcessedClients.filter { !currentProcessedClients.contains(it) }
      val unconfiguredNewClients = detectedClients.filter { it.name.displayName in newClients }.filterNot { it.isConfigured() ?: false }
      if (unconfiguredNewClients.isNotEmpty()) {
        showMcpServerAutomaticConfigurationNotification(project, unconfiguredNewClients)
      }
    }
  }

  private class ShowSettingsAction(private val project: Project, text: String = McpServerBundle.message("mcp.unconfigured.clients.detected.configure.settings.json")) : AnAction(text) {
    override fun actionPerformed(e: AnActionEvent) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, McpServerSettingsConfigurable::class.java)
    }
  }

  private class AutoconfigureAction(private val project: Project, private val unconfiguredClients: List<McpClient>, private val notification: Notification) : AnAction(McpServerBundle.message("mcp.unconfigured.clients.detected.configure.json")) {
    override fun actionPerformed(e: AnActionEvent) {
      val clientsWithErrorDuringConfiguration = mutableSetOf<McpClient>()
      unconfiguredClients.forEach { client ->
        runCatching { client.configure() }.onFailure {
          thisLogger().info(it)
          clientsWithErrorDuringConfiguration.add(client)
        }
      }
      val configuredClients = unconfiguredClients.filter { it !in clientsWithErrorDuringConfiguration }
      if (configuredClients.isNotEmpty()) {
        val doneNotification = NotificationGroupManager.getInstance().getNotificationGroup("MCP Server")
          .createNotification(McpServerBundle.message("mcp.client.autoconfigured"),
                              McpServerBundle.message("mcp.server.client.restart.info", configuredClients.joinToString(", ") { it.name.displayName }), NotificationType.INFORMATION)
          .setImportant(false)
        doneNotification.notify(project)
      }

      if (clientsWithErrorDuringConfiguration.isNotEmpty()) {
        val errorNotification = NotificationGroupManager.getInstance().getNotificationGroup("MCP Server")
          .createNotification(McpServerBundle.message("mcp.client.error.autoconfigured"),
                              McpServerBundle.message("mcp.server.error.autoconfigured.info", clientsWithErrorDuringConfiguration.joinToString(", ") { it.name.displayName }), NotificationType.WARNING)
          .setImportant(false)
        errorNotification.notify(project)
      }
      notification.expire()
    }
  }

  private fun showMcpServerAutomaticConfigurationNotification(project: Project, unconfiguredClients: List<McpClient>) {
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("MCP Server")
      .createNotification(
        McpServerBundle.message("mcp.unconfigured.clients.detected.notification.title"),
        McpServerBundle.message("mcp.unconfigured.clients.detected.notification.message", unconfiguredClients.joinToString(", ") { it.name.displayName }),
        NotificationType.INFORMATION
      )
    notification
      .addAction(AutoconfigureAction(project, unconfiguredClients, notification))
      .addAction(ShowSettingsAction(project)).notify(project)
  }

  private fun showMcpServerEnablingSuggestionNotification(project: Project, detectedClients: List<McpClient>) {
    val currentProcessedClients = application.service<McpClientDetectionSettings>().state.processedClients.toMutableSet()
    val newProcessedClients = (currentProcessedClients + detectedClients.map { it.name.displayName }).toMutableSet()

    if (currentProcessedClients == newProcessedClients) return

    application.service<McpClientDetectionSettings>().state.processedClients = newProcessedClients
    application.service<McpClientDetectionSettings>().state.intIncrementModificationCount()

    val newClients = newProcessedClients.filter { !currentProcessedClients.contains(it) }
    val clientNames = newClients.joinToString(", ") { it }
    NotificationGroupManager.getInstance()
      .getNotificationGroup("MCP Server")
      .createNotification(
        McpServerBundle.message("mcp.clients.detected.notification.title"),
        McpServerBundle.message("mcp.clients.detected.notification.message", clientNames, ApplicationNamesInfo.getInstance().fullProductName),
        NotificationType.INFORMATION
      )
      .addAction(ShowSettingsAction(project, McpServerBundle.message("mcp.clients.detected.action.enable")))
      .addAction(object : AnAction(McpServerBundle.message("mcp.clients.detected.action.show.help")) {
        override fun actionPerformed(e: AnActionEvent) {
          BrowserUtil.open("https://modelcontextprotocol.io/introduction")
        }
      })
      .addAction(object : AnAction(McpServerBundle.message("mcp.clients.detected.action.dont.show")) {
        override fun actionPerformed(e: AnActionEvent) {
          application.service<McpClientDetectionSettings>().state.doNotShowServerDisabledAgain = true
        }
      })
      .notify(project)
  }

}