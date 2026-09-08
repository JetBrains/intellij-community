package com.intellij.mcpserver.clients

import org.jetbrains.annotations.TestOnly

/**
 * Describes an MCP client integration, capturing its display name and configuration scope.
 * `name` identifies the target product, while `scope` differentiates global and project-level configs.
 */
data class McpClientInfo(
  val name: Name,
  val scope: McpClientScope,
) {

  val displayName: String = "${name.baseName}${if (scope is McpClientScope.McpClientProjectScope) " (Project)" else ""}"

  enum class Name(val baseName: String) {
    VS_CODE("Visual Studio Code"),
    CLAUDE_APP("Claude App"),
    WINDSURF("Windsurf"),
    CURSOR("Cursor"),
    CLAUDE_CODE("Claude Code"),
    JUNIE("Junie"),
    CODEX("Codex"),
    AIR("Air"),
    GITHUB_COPILOT_IDE_PLUGIN("GitHub Copilot (IDE Plugin)"),
    GITHUB_COPILOT_CLI("GitHub Copilot CLI"),
  }

  sealed class McpClientScope {
    data object McpClientGlobalScope: McpClientScope()
    class McpClientProjectScope : McpClientScope {
      val projectPath: String?

      constructor(project: com.intellij.openapi.project.Project) {
        this.projectPath = project.basePath
      }

      @TestOnly
      constructor(projectPath: String?) {
        this.projectPath = projectPath
      }
    }
  }
}
