package org.jetbrains.mcpserverplugin

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.mcpserverplugin.notification.ClaudeConfigManager
import org.jetbrains.mcpserverplugin.settings.PluginSettings

internal class MCPServerStartupValidator : ProjectActivity {
    private val GROUP_ID = "MCPServerPlugin"

    fun isNpxInstalled(): Boolean {
        return try {
            val process = ProcessBuilder().apply {
                if (System.getProperty("os.name").lowercase().contains("windows")) {
                    command("where", "npx")
                } else {
                    command("which", "npx")
                }
            }.start()

            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun execute(project: Project) {
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
        val settingsService = service<PluginSettings>()
        if (!ClaudeConfigManager.isClaudeClientInstalled() && settingsService.state.shouldShowClaudeNotification) {
            val notification = notificationGroup.createNotification(
                "Claude Client is not installed",
                NotificationType.INFORMATION
            )
            notification.addAction(NotificationAction.createSimpleExpiring("Open Installation Instruction") {
                BrowserUtil.open("https://claude.ai/download")
            })
            notification.addAction(NotificationAction.createSimpleExpiring("Don't Show Again") {
                settingsService.state.shouldShowClaudeNotification = false
                notification.expire()
            })
            notification.notify(project)
        }

        if (!isNpxInstalled() && settingsService.state.shouldShowNodeNotification) {
            val notification = notificationGroup.createNotification(
                "Node is not installed",
                "MCP Server Proxy requires Node.js to be installed",
                NotificationType.INFORMATION
            )
            notification.addAction(NotificationAction.createSimpleExpiring("Open Installation Instruction") {
                BrowserUtil.open("https://nodejs.org/en/download/package-manager")
            })
            notification.addAction(NotificationAction.createSimpleExpiring("Don't Show Again") {
                settingsService.state.shouldShowNodeNotification = false
                notification.expire()
            })
            notification.notify(project)
        }

        if (ClaudeConfigManager.isClaudeClientInstalled() && isNpxInstalled() && !ClaudeConfigManager.isProxyConfigured() && settingsService.state.shouldShowClaudeSettingsNotification) {
            val notification = notificationGroup.createNotification(
                "MCP Server Proxy is not configured",
                NotificationType.INFORMATION
            )
            notification.addAction(NotificationAction.createSimpleExpiring("Install MCP Server Proxy") {
                ClaudeConfigManager.modifyClaudeSettings()
            })
            notification.addAction(NotificationAction.createSimpleExpiring("Don't Show Again") {
                settingsService.state.shouldShowClaudeSettingsNotification = false
                notification.expire()
            })
            notification.notify(project)
        }
    }
}