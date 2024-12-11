package org.jetbrains.mcpserverplugin

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.mcpserverplugin.notification.ClaudeConfigManager

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
        if (!ClaudeConfigManager.isClaudeClientInstalled()) {
            val notification = notificationGroup.createNotification(
                "Claude Client is not installed",
                NotificationType.INFORMATION
            )
            notification.addAction(NotificationAction.createSimpleExpiring("Open Installation Instruction") {
                BrowserUtil.open("https://claude.ai/download")
            })
            notification.notify(project)
        }

        if(!isNpxInstalled()){
            val notification = notificationGroup.createNotification(
                "Node is not installed",
                "MCP Server Proxy requires Node.js to be installed",
                NotificationType.INFORMATION
            )
            notification.addAction(NotificationAction.createSimpleExpiring("Open Installation Instruction") {
                BrowserUtil.open("https://nodejs.org/en/download/package-manager")
            })
            notification.notify(project)
        }

        if (ClaudeConfigManager.isClaudeClientInstalled() && isNpxInstalled() && !ClaudeConfigManager.isProxyConfigured()) {
            val notification = notificationGroup.createNotification(
                "MCP Server Proxy is not configured",
                NotificationType.INFORMATION
            )
            notification.addAction(NotificationAction.createSimpleExpiring("Install MCP Server Proxy") {
                ClaudeConfigManager.modifyClaudeSettings()
            })
            notification.notify(project)
        }
    }
}