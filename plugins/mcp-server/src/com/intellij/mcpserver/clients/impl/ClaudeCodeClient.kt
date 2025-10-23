package com.intellij.mcpserver.clients.impl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ClaudeCodeSSEConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import java.nio.file.Path

class ClaudeCodeClient(scope: McpClientInfo.Scope, configPath: Path) : McpClient(
  mcpClientInfo = McpClientInfo(McpClientInfo.Name.CLAUDE_CODE, scope),
  configPath = configPath
) {

  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val sse = isSSEConfigured() ?: return null
    return stdio || sse
  }

  override fun configure() {
    runWithModalProgressBlocking(
      ModalTaskOwner.guess(),
      McpServerBundle.message("autoconfigure.progress.title"),
      TaskCancellation.nonCancellable()
    ) {
      fun add(name: String, url: String) {
        val cmd = GeneralCommandLine()
          .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
          .withExePath("claude")
          .withParameters("mcp", "add", "--scope", "user", "--transport", "sse", name, url)
        val out = ExecUtil.execAndGetOutput(cmd, 1000)
        if (out.exitCode != 0) {
          error("Claude failed with exit code ${out.exitCode}: ${out.stderr}")
        }
      }

      val prod = jetBrainsServerKey()
      add(prod, sseUrl)
      if (writeLegacy() && LEGACY_KEY != prod) {
        add(LEGACY_KEY, sseUrl)
      }
    }
  }

  override fun getSSEConfig(): ServerConfig = ClaudeCodeSSEConfig(type = "sse", url = sseUrl)
}