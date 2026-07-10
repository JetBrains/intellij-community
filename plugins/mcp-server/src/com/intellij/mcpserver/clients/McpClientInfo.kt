package com.intellij.mcpserver.clients

import org.jetbrains.annotations.TestOnly

/**
 * Describes an MCP client integration, capturing its display name and configuration scope.
 * `name` identifies the target product, while `scope` differentiates global and project-level configs.
 */
data class McpClientInfo(
  val name: Name,
  val scope: Scope,
) {

  val displayName: String = "${name.baseName}${if (scope is Scope.Project) " (Project)" else ""}"

  enum class Name(val baseName: String) {
    VS_CODE("VSCode"),
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

  sealed class Scope {
    data object Global: Scope()
    class Project : Scope {
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
