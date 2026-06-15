package com.intellij.mcpserver.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.impl.AirClient
import com.intellij.mcpserver.clients.impl.ClaudeClient
import com.intellij.mcpserver.clients.impl.ClaudeCodeClient
import com.intellij.mcpserver.clients.impl.CodexClient
import com.intellij.mcpserver.clients.impl.CursorClient
import com.intellij.mcpserver.clients.impl.GitHubCopilotCliClient
import com.intellij.mcpserver.clients.impl.GitHubCopilotIdePluginClient
import com.intellij.mcpserver.clients.impl.JunieClient
import com.intellij.mcpserver.clients.impl.VSCodeClient
import com.intellij.mcpserver.clients.impl.WindsurfClient
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.util.containers.addIfNotNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

object McpClientDetector {
  fun detectGlobalMcpClients(): List<McpClient> {
    val globalClients = mutableListOf<McpClient>()

    runCatching {
      globalClients.addIfNotNull(detectJunie())
    }
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
    runCatching {
      globalClients.addIfNotNull(detectCodex())
    }
    runCatching {
      globalClients.addIfNotNull(detectAir())
    }
    runCatching {
      globalClients.addIfNotNull(detectGitHubCopilotCli())
    }
    runCatching {
      globalClients.addIfNotNull(detectGitHubCopilotJetBrains())
    }

    return globalClients
  }

  internal fun detectProjectMcpClients(project: Project): List<McpClient> {
    val projectClients = mutableListOf<McpClient>()

    runCatching {
      projectClients.addIfNotNull(detectVSCode(project))
    }
    runCatching {
      projectClients.addIfNotNull(detectCursorProject(project))
    }
    runCatching {
      projectClients.addIfNotNull(detectJunieProject(project))
    }
    runCatching {
      projectClients.addIfNotNull(detectClaudeCode(project))
    }
    runCatching {
      projectClients.addIfNotNull(detectCodex(project))
    }
    runCatching {
      projectClients.addIfNotNull(detectAirInProject(project))
    }
    runCatching {
      projectClients.addIfNotNull(detectGitHubCopilotCliProject(project))
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
      SystemInfo.isMac -> "~/Library/Application Support/Code/User/mcp.json"
      SystemInfo.isWindows -> System.getenv("APPDATA")?.let { "$it/Code/User/mcp.json" }
      SystemInfo.isLinux -> "~/.config/Code/User/mcp.json"
      else -> null
    }
    if (configPath == null) return null
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome(configPath))
    if (path.exists() && path.isRegularFile()) {
      return VSCodeClient(McpClientInfo.Scope.Global, path)
    }
    return null
  }

  private fun detectClaudeCode(): McpClient? {
    val configPath = "~/.claude.json"
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome(configPath))

    if (path.exists() && path.isRegularFile()) {
      return ClaudeCodeClient(McpClientInfo.Scope.Global, path)
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

    if (path.parent.exists() && path.isDirectory()) {
      return ClaudeClient(McpClientInfo.Scope.Global, path)
    }
    return null
  }

  private fun detectJunie(): McpClient? {
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome("~/.junie/mcp/mcp.json"))
    if (path.parent.exists() && Files.isDirectory(path.parent)) {
      return JunieClient(McpClientInfo.Scope.Global, path)
    }
    return null
  }

  private fun detectJunieProject(project: Project): McpClient? {
    val projectBasePath = project.basePath ?: return null
    val configPath = Paths.get(projectBasePath, ".junie", "mcp", "mcp.json")
    if (looksLikeMcpJson(configPath)) {
      return JunieClient(McpClientInfo.Scope.Project(project), configPath)
    }
    return null
  }

  private fun detectCursorGlobal(): McpClient? {
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome("~/.cursor/mcp.json"))
    if (path.parent.exists() && path.isDirectory()) {
      return CursorClient(McpClientInfo.Scope.Global, path)
    }
    return null
  }

  private fun detectWindsurf(): McpClient? {
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome("~/.codeium/windsurf/mcp_config.json"))
    if (path.parent.exists() && path.isDirectory()) {
      return WindsurfClient(McpClientInfo.Scope.Global, path)
    }
    return null
  }

  private fun detectCodex(): McpClient? {
    val path = resolveCodexConfigPath() ?: return null
    return CodexClient(McpClientInfo.Scope.Global, path)
  }

  private fun detectVSCode(project: Project): McpClient? {
    val configDirName = ".vscode"
    val projectBasePath = project.basePath ?: return null
    val configPath = Paths.get(projectBasePath, configDirName, "mcp.json")
    if (looksLikeMcpJson(configPath)) {
      return VSCodeClient(McpClientInfo.Scope.Project(project), configPath)
    }
    return null
  }

  private fun detectCursorProject(project: Project): McpClient? {
    val configDirName = ".cursor"
    val projectBasePath = project.basePath ?: return null
    val configPath = Paths.get(projectBasePath, configDirName, "mcp.json")

    if (looksLikeMcpJson(configPath)) {
      return CursorClient(McpClientInfo.Scope.Project(project), configPath)
    }
    return null
  }

  private fun detectClaudeCode(project: Project): McpClient? {
    val projectBasePath = project.basePath ?: return null
    val claudeCodeConfigPath = Paths.get(projectBasePath, ".mcp.json")

    if (looksLikeMcpJson(claudeCodeConfigPath)) {
      return ClaudeCodeClient(McpClientInfo.Scope.Project(project), claudeCodeConfigPath)
    }
    return null
  }

  private fun detectCodex(project: Project): McpClient? {
    val projectBasePath = project.basePath ?: return null
    val configPath = Paths.get(projectBasePath, ".codex", "config.toml")
    val parent = configPath.parent
    if (configPath.exists() && configPath.isRegularFile() || parent != null && parent.exists() && parent.isDirectory()) {
      return CodexClient(McpClientInfo.Scope.Project(project), configPath)
    }
    if (parent != null && !parent.exists()) {
      return CodexClient(McpClientInfo.Scope.Project(project), configPath)
    }
    return null
  }

  private fun detectAir(): McpClient? {
    val configPath = when {
      SystemInfo.isMac -> "~/Library/Application Support/JetBrains/Air/mcp.json"
      SystemInfo.isWindows -> System.getenv("APPDATA")?.let { "$it/JetBrains/Air/mcp.json" }
      SystemInfo.isLinux -> {
        val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")
        val base = if (xdgConfigHome.isNullOrEmpty()) "~/.config" else xdgConfigHome
        "$base/JetBrains/Air/mcp.json"
      }
      else -> null
    }
    if (configPath == null) return null
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome(configPath))
    if (path.parent.exists() && Files.isDirectory(path.parent)) {
      return AirClient(McpClientInfo.Scope.Global, path)
    }
    return null
  }

  private fun detectAirInProject(project: Project): McpClient? {
    val projectBasePath = project.basePath ?: return null
    val configPath = Paths.get(projectBasePath, ".air", "mcp.json")
    if (looksLikeMcpJson(configPath)) {
      return AirClient(McpClientInfo.Scope.Project(project), configPath)
    }
    return null
  }

  private fun detectGitHubCopilotCli(): McpClient? {
    val copilotHome = System.getenv("COPILOT_HOME")
    val homeDir = if (copilotHome.isNullOrEmpty()) {
      Paths.get(OSAgnosticPathUtil.expandUserHome("~/.copilot"))
    }
    else {
      Paths.get(copilotHome)
    }
    if (!homeDir.exists() || !Files.isDirectory(homeDir)) return null

    val candidates = listOf(homeDir.resolve("mcp.json"), homeDir.resolve("mcp-config.json"))
    val path = candidates.firstOrNull { it.exists() && it.isRegularFile() } ?: candidates.first()
    return GitHubCopilotCliClient(McpClientInfo.Scope.Global, path)
  }

  private fun detectGitHubCopilotCliProject(project: Project): McpClient? {
    val projectBasePath = project.basePath ?: return null
    val candidates = listOf(
      Paths.get(projectBasePath, ".github", "mcp.json"),
      Paths.get(projectBasePath, "mcp.json"),
    )
    val configPath = candidates.firstOrNull { looksLikeMcpJson(it) } ?: return null
    return GitHubCopilotCliClient(McpClientInfo.Scope.Project(project), configPath)
  }

  private fun detectGitHubCopilotJetBrains(): McpClient? {
    val configPath = when {
      SystemInfo.isMac -> "~/.config/github-copilot/intellij/mcp.json"
      SystemInfo.isLinux -> "~/.config/github-copilot/intellij/mcp.json"
      SystemInfo.isWindows -> System.getenv("LOCALAPPDATA")?.let { "$it/github-copilot/intellij/mcp.json" }
      else -> null
    }
    if (configPath == null) return null
    val path = Paths.get(OSAgnosticPathUtil.expandUserHome(configPath))
    if (path.parent.exists() && Files.isDirectory(path.parent)) {
      return GitHubCopilotIdePluginClient(McpClientInfo.Scope.Global, path)
    }
    return null
  }

  fun preferredCodexConfigPath(): Path? {
    return resolveCodexConfigPath() ?: codexConfigCandidates().firstOrNull()
  }

  private fun resolveCodexConfigPath(): Path? {
    val candidates = codexConfigCandidates()

    if (candidates.isEmpty()) return null

    candidates.firstOrNull { it.exists() && it.isRegularFile() }?.let { return it }
    candidates.firstOrNull { path ->
      val parent = path.parent
      parent != null && parent.exists() && parent.isDirectory()
    }?.let { return it }
    return null
  }

  private fun codexConfigCandidates(): List<Path> {
    return buildList {
      add(Paths.get(OSAgnosticPathUtil.expandUserHome("~/.codex/config.toml")))
      if (SystemInfo.isMac) {
        add(Paths.get(OSAgnosticPathUtil.expandUserHome("~/Library/Application Support/Codex/config.toml")))
      }
      if (SystemInfo.isLinux) {
        add(Paths.get(OSAgnosticPathUtil.expandUserHome("~/.config/codex/config.toml")))
      }
      if (SystemInfo.isWindows) {
        System.getenv("APPDATA")?.let { add(Paths.get(it, "Codex", "config.toml")) }
      }
    }
  }
}
