package com.intellij.mcpserver.clients

/**
 * Describes an MCP client integration, capturing its display name and configuration scope.
 * `name` identifies the target product, while `scope` differentiates global and project-level configs.
 */
data class McpClientInfo(
  val name: Name,
  val scope: Scope,
) {

  val displayName: String = "${name.baseName}${if (scope == Scope.PROJECT) " (Project)" else ""}"

  enum class Name(val baseName: String) {
    VS_CODE("VSCode"),
    CLAUDE_APP("Claude App"),
    WINDSURF("Windsurf"),
    CURSOR("Cursor"),
    CLAUDE_CODE("Claude Code"),
    CODEX("Codex");
  }

  enum class Scope {
    GLOBAL, PROJECT
  }
}
