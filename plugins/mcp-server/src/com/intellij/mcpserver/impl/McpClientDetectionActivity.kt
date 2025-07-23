package com.intellij.mcpserver.impl

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clientConfiguration.McpClient
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.settings.McpServerSettingsConfigurable
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.application

private const val MODEL_CONTEXT_INTRO_URL = "https://modelcontextprotocol.io/introduction"

private const val DETECTED_NOTIFICATION_ID = "mcp.client.detected"

@Service(Service.Level.APP)
@State(name = "McpNotificationSettings", storages = [Storage("mcpNotification.xml", roamingType = RoamingType.DISABLED)])
internal class McpClientDetectionSettings : SimplePersistentStateComponent<McpClientDetectionSettings.MyState>(MyState()) {
  internal class MyState : BaseState() {
    var processedClients: MutableSet<String> by stringSet()
  }
}

internal class McpClientDetectionActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (ConfigImportHelper.isNewUser()) return // not just yet, next time

    val detectedClients = McpClientDetector.detectMcpClients(project)
    suggestToChangePortIfNeeded(detectedClients, project)

    if (Registry.`is`("mcp.server.detect.mcp.clients")) {
      if (McpServerSettings.getInstance().state.enableMcpServer) {
        showUnconfiguredNotificationIfNeeded(detectedClients, project)
        return
      }

      if (Notification.isDoNotAskFor(project, DETECTED_NOTIFICATION_ID)) return

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
        .setDisplayId("mcp.client.wrong.port.detected")

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
    val state = application.service<McpClientDetectionSettings>().state
    val currentProcessedClients = state.processedClients.toMutableSet()
    val newProcessedClients = (currentProcessedClients + detectedClients.map { it.name.displayName }).toMutableSet()

    if (currentProcessedClients != newProcessedClients) {
      state.processedClients = newProcessedClients
      state.intIncrementModificationCount()

      val newClients = newProcessedClients.filter { !currentProcessedClients.contains(it) }
      val unconfiguredNewClients = detectedClients.filter { it.name.displayName in newClients }.filterNot { it.isConfigured() ?: false }
      if (unconfiguredNewClients.isNotEmpty()) {
        showMcpServerAutomaticConfigurationNotification(project, unconfiguredNewClients)
      }
    }
  }

  private class ShowSettingsAction(private val project: Project, @NlsActions.ActionText text: String = McpServerBundle.message("mcp.unconfigured.clients.detected.configure.settings.json")) : AnAction(text) {
    override fun actionPerformed(e: AnActionEvent) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, McpServerSettingsConfigurable::class.java)
    }
  }

  private class AutoconfigureAction(private val project: Project, private val unconfiguredClients: List<McpClient>, private val notification: Notification) : AnAction(McpServerBundle.message("mcp.unconfigured.clients.detected.configure.json")) {
    override fun actionPerformed(e: AnActionEvent) {
      val clientsWithErrorDuringConfiguration = mutableSetOf<McpClient>()
      for (client in unconfiguredClients) {
        try {
          client.configure()
        }
        catch (t: Throwable) {
          thisLogger().warn(t)

          clientsWithErrorDuringConfiguration.add(client)
        }
      }

      val configuredClients = unconfiguredClients.filter { it !in clientsWithErrorDuringConfiguration }
      if (configuredClients.isNotEmpty()) {
        val doneNotification = NotificationGroupManager.getInstance().getNotificationGroup("MCP Server")
          .createNotification(McpServerBundle.message("mcp.client.autoconfigured"),
                              McpServerBundle.message("mcp.server.client.restart.info", configuredClients.joinToString(", ") { it.name.displayName }), NotificationType.INFORMATION)
          .setDisplayId("mcp.client.autoconfigured")
        doneNotification.notify(project)
      }

      if (clientsWithErrorDuringConfiguration.isNotEmpty()) {
        val errorNotification = NotificationGroupManager.getInstance().getNotificationGroup("MCP Server")
          .createNotification(McpServerBundle.message("mcp.client.error.autoconfigured"),
                              McpServerBundle.message("mcp.server.error.autoconfigured.info", clientsWithErrorDuringConfiguration.joinToString(", ") { it.name.displayName }), NotificationType.WARNING)
          .setDisplayId("mcp.client.error.autoconfigured")
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
    val state = application.service<McpClientDetectionSettings>().state

    val currentProcessedClients = state.processedClients.toMutableSet()
    val newProcessedClients = (currentProcessedClients + detectedClients.map { it.name.displayName }).toMutableSet()

    if (currentProcessedClients == newProcessedClients) return

    state.processedClients = newProcessedClients
    state.intIncrementModificationCount()

    val newClients = newProcessedClients.filter { !currentProcessedClients.contains(it) }
    val clientNames = newClients.joinToString(", ") { it }

    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("MCP Server")
      .createNotification(
        McpServerBundle.message("mcp.clients.detected.notification.title"),
        McpServerBundle.message("mcp.clients.detected.notification.message", clientNames),
        NotificationType.INFORMATION
      )
      .setDisplayId(DETECTED_NOTIFICATION_ID)

    notification.configureDoNotAskOption(DETECTED_NOTIFICATION_ID, McpServerBundle.message("mcp.clients.detected.action.enable"))

    notification
      .addAction(ShowSettingsAction(project, McpServerBundle.message("mcp.clients.detected.action.enable")))
      .addAction(NotificationAction.createSimple(McpServerBundle.message("mcp.clients.detected.action.show.help")) {
        BrowserUtil.open(MODEL_CONTEXT_INTRO_URL)
      })
      .addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("label.dont.show")) {
        notification.setDoNotAskFor(null)
        notification.expire()
      })
      .notify(project)
  }
}