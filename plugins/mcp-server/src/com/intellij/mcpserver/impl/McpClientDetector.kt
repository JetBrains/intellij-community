package com.intellij.mcpserver.impl

import com.intellij.mcpserver.clientConfiguration.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.addIfNotNull
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

object McpClientDetector {
  fun detectMcpClients(project: Project): List<McpClient> {
    val detectedClients = mutableListOf<McpClient>()

    detectedClients.addAll(detectGlobalMcpClients())

    detectedClients.addAll(detectProjectMcpClients(project))

    return detectedClients
  }

  fun detectGlobalMcpClients(): List<McpClient> {
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
    runCatching {
      globalClients.addIfNotNull(detectVSCode())
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

  private fun detectVSCode(): McpClient? {
    val configPath = when {
      SystemInfo.isMac -> "~/Library/Application Support/Code/User/settings.json"
      SystemInfo.isWindows -> System.getenv("APPDATA")?.let { "$it/Code/User/settings.json" }
      SystemInfo.isLinux -> "~/.config/Code/User/settings.json"
      else -> null
    }
    if (configPath == null) return null
    val path = Paths.get(FileUtil.expandUserHome(configPath))
    if (path.exists() && path.isRegularFile()) {
      return VSCodeClient("VSCode (Global)", path)
    }
    return null
  }

  private fun detectClaudeDesktop(): McpClient? {
    val configPath = when {
      SystemInfo.isMac -> "~/Library/Application Support/Claude/claude_desktop_config.json"
      SystemInfo.isWindows -> System.getenv("APPDATA")?.let { "$it/Claude/claude_desktop_config.json" }
      SystemInfo.isLinux -> "~/.config/Claude/claude_desktop_config.json"
      else -> null
    }
    if (configPath == null) return null
    val path = Paths.get(FileUtil.expandUserHome(configPath))

    if (path.exists() && path.isRegularFile()) {
      return ClaudeMcpClient("Claude Desktop (Global)", path)
    }
    return null
  }

  private fun detectCursorGlobal(): McpClient? {
    val path = Paths.get(FileUtil.expandUserHome("~/.cursor/mcp.json"))
    if (path.exists() && path.isRegularFile()) {
      return CursorClient("Cursor (Global)", path)
    }
    return null
  }

  private fun detectWindsurf(): McpClient? {
    val path = Paths.get(FileUtil.expandUserHome("~/.codeium/windsurf/mcp_config.json"))
    if (path.exists() && path.isRegularFile()) {
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
}