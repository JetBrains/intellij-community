package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clientConfiguration.ClaudeMcpClient
import com.intellij.mcpserver.clientConfiguration.CursorClient
import com.intellij.mcpserver.clientConfiguration.McpClient
import com.intellij.mcpserver.clientConfiguration.WindsurfClient
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
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.containers.addIfNotNull
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

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

      val detectedClients = detectMcpClients(project)
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

  private fun detectMcpClients(project: Project): List<McpClient> {
    val detectedClients = mutableListOf<McpClient>()

    detectedClients.addAll(detectGlobalMcpClients())
    
    detectedClients.addAll(detectProjectMcpClients(project))

    return detectedClients
  }

  private fun detectGlobalMcpClients(): List<McpClient> {
    val globalClients = mutableListOf<McpClient>()

    runCatching {
      globalClients.addIfNotNull(detectClaudeDesktop())
    }
    runCatching {
      globalClients.addIfNotNull(detectCursorGlobal())
    }
    runCatching {
      globalClients.addIfNotNull(detectWindsurf())
    }

    return globalClients
  }

  private fun detectProjectMcpClients(project: Project): List<McpClient> {
    val projectClients = mutableListOf<McpClient>()

    runCatching {
      projectClients.addIfNotNull(detectVSCode(project))
    }
    runCatching {
      projectClients.addIfNotNull(detectCursorProject(project))
    }
    runCatching {
      projectClients.addIfNotNull(detectClaudeCode(project))
    }

    return projectClients
  }

  private fun looksLikeMcpJson(file: Path): Boolean {
    if (file.exists() && file.isRegularFile()) {
      val content = runCatching { file.readText() }.getOrElse { "" }
      return content.contains("mcpServers")
    }
    return false
  }

  private fun detectClaudeDesktop(): McpClient? {
    val configPath = when {
      SystemInfo.isMac -> "~/Library/Application Support/Claude/claude_desktop_config.json"
      SystemInfo.isWindows -> System.getenv("APPDATA")?.let { "$it/Claude/claude_desktop_config.json" }
      SystemInfo.isLinux -> "~/.config/Claude/claude_desktop_config.json"
      else -> null
    }
    if (configPath == null) return null
    val expandedPath = FileUtil.expandUserHome(configPath)
    val path = Paths.get(expandedPath)

    if (looksLikeMcpJson(path)) {
      return ClaudeMcpClient("Claude Desktop (Global)", path)
    }
    return null
  }

  private fun detectCursorGlobal(): McpClient? {
    val path = Paths.get(FileUtil.expandUserHome("~/.cursor/mcp.json"))
    if (looksLikeMcpJson(path)) {
      return CursorClient("Cursor (Global)", path)
    }
    return null
  }

  private fun detectWindsurf(): McpClient? {
    val path = Paths.get("~/.codeium/windsurf/mcp_config.json")
    if (looksLikeMcpJson(path)) {
      return WindsurfClient("Windsurf (Global)", path)
    }
    return null
  }

  private fun detectProjectLevelClient(project: Project, configDirName: String, clientName: String): McpClient? {
    val projectBasePath = project.basePath ?: return null
    val vscodeConfigPath = Paths.get(projectBasePath, configDirName, "mcp.json")

    if (looksLikeMcpJson(vscodeConfigPath)) {
      return McpClient("$clientName (Project)", vscodeConfigPath)
    }
    return null
  }

  private fun detectVSCode(project: Project): McpClient? {
    val configDirName = ".vscode"
    val clientName = "VSCode"
    return detectProjectLevelClient(project, configDirName, clientName)
  }

  private fun detectCursorProject(project: Project): McpClient? {
    val configDirName = ".cursor"
    val clientName = "Cursor"
    return detectProjectLevelClient(project, configDirName, clientName)
  }

  private fun detectClaudeCode(project: Project): McpClient? {
    val projectBasePath = project.basePath ?: return null
    val claudeCodeConfigPath = Paths.get(projectBasePath, ".mcp.json")

    if (looksLikeMcpJson(claudeCodeConfigPath)) {
      return McpClient("Claude Code (Project)", claudeCodeConfigPath)
    }
    return null
  }

  private fun showMcpServerAutomaticConfigurationNotification(project: Project, unconfiguredClients: List<McpClient>) {
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("MCP Server")
      .createNotification(
        McpServerBundle.message("mcp.unconfigured.clients.detected.notification.title"),
        McpServerBundle.message("mcp.unconfigured.clients.detected.notification.message", unconfiguredClients),
        NotificationType.INFORMATION
      )
    notification.addAction(object : AnAction(McpServerBundle.message("mcp.unconfigured.clients.detected.configure.json")) {
      override fun actionPerformed(e: AnActionEvent) {
        unconfiguredClients.forEach { it.configure() }
        notification.expire()
      }
    }).addAction(object : AnAction(McpServerBundle.message("mcp.clients.detected.action.dont.show")) {
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