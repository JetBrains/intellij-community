package com.intellij.mcpserver.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.impl.ClaudeClient
import com.intellij.mcpserver.clients.impl.ClaudeCodeClient
import com.intellij.mcpserver.clients.impl.CursorClient
import com.intellij.mcpserver.clients.impl.VSCodeClient
import com.intellij.mcpserver.clients.impl.WindsurfClient
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.util.containers.addIfNotNull
import kotlinx.serialization.ExperimentalSerializationApi
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
      globalClients.addIfNotNull(detectVSCode())
    }
    runCatching {
      globalClients.addIfNotNull(detectCursorGlobal())
    }
    runCatching {
      globalClients.addIfNotNull(detectClaudeDesktop())
    }
    runCatching {
      globalClients.addIfNotNull(detectClaudeCode())
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

  @OptIn(ExperimentalSerializationApi::class)
  private fun detectVSCode(): McpClient? {
    val configPath = when {
      SystemInfo.isMac -> "~/Library/Application Support/Code/User/mcp.json"
      SystemInfo.isWindows -> System.getenv("APPDATA")?.let { "$it/Code/User/mcp.json" }
      SystemInfo.isLinux -> "~/.config/Code/User/mcp.json"
      else -> null
    }
    if (configPath == null) return null
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome(configPath))
    if (path.exists() && path.isRegularFile()) {
      return VSCodeClient(McpClientInfo.Scope.GLOBAL, path)
    }
    return null
  }

  private fun detectClaudeCode(): McpClient? {
    val configPath = when {
      SystemInfo.isMac -> "~/.claude.json"
      SystemInfo.isWindows -> null
      SystemInfo.isLinux -> "~/.claude.json"
      else -> null
    }
    if (configPath == null) return null
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome(configPath))

    if (path.exists() && path.isRegularFile()) {
      return ClaudeCodeClient(McpClientInfo.Scope.GLOBAL, path)
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
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome(configPath))

    if (path.parent.exists() && path.parent.toFile().isDirectory()) {
      return ClaudeClient(McpClientInfo.Scope.GLOBAL, path)
    }
    return null
  }

  private fun detectCursorGlobal(): McpClient? {
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome("~/.cursor/mcp.json"))
    if (path.parent.exists() && path.parent.toFile().isDirectory()) {
      return CursorClient(McpClientInfo.Scope.GLOBAL, path)
    }
    return null
  }

  private fun detectWindsurf(): McpClient? {
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome("~/.codeium/windsurf/mcp_config.json"))
    if (path.parent.exists() && path.parent.toFile().isDirectory()) {
      return WindsurfClient(McpClientInfo.Scope.GLOBAL, path)
    }
    return null
  }

  private fun detectVSCode(project: Project): McpClient? {
    val configDirName = ".vscode"
    val projectBasePath = project.basePath ?: return null
    val configPath = Paths.get(projectBasePath, configDirName, "mcp.json")
    if (looksLikeMcpJson(configPath)) {
      return VSCodeClient(McpClientInfo.Scope.PROJECT, configPath)
    }
    return null
  }

  private fun detectCursorProject(project: Project): McpClient? {
    val configDirName = ".cursor"
    val projectBasePath = project.basePath ?: return null
    val configPath = Paths.get(projectBasePath, configDirName, "mcp.json")

    if (looksLikeMcpJson(configPath)) {
      return CursorClient(McpClientInfo.Scope.PROJECT, configPath)
    }
    return null
  }

  private fun detectClaudeCode(project: Project): McpClient? {
    val projectBasePath = project.basePath ?: return null
    val claudeCodeConfigPath = Paths.get(projectBasePath, ".mcp.json")

    if (looksLikeMcpJson(claudeCodeConfigPath)) {
      return ClaudeCodeClient(McpClientInfo.Scope.PROJECT, claudeCodeConfigPath)
    }
    return null
  }
}
