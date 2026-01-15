package com.intellij.mcpserver.clients.impl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ClaudeCodeSSEConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import java.nio.file.Path

private val logger = logger<ClaudeCodeClient>()

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
      fun remove(name: String) {
        val removeCmd = GeneralCommandLine().withExePath("claude").withParameters("mcp", "remove", "--scope", "user", name)
        val output = ExecUtil.execAndGetOutput(removeCmd, 1000)
        logger.trace { "Claude remove mcp stdout: ${output.stdout}" }
        logger.trace { "Claude remove mcp stderr: ${output.stderr}" }
      }

      fun add(name: String, url: String) {
        val addCmd = GeneralCommandLine()
          .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
          .withExePath("claude")
          .withParameters("mcp", "add", "--scope", "user", "--transport", "sse", name, url)
        val out = ExecUtil.execAndGetOutput(addCmd, 1000)
        if (out.exitCode != 0) {
          error("Claude failed with exit code ${out.exitCode}: ${out.stderr}")
        }
      }

      val productKey = productSpecificServerKey()
      remove(productKey)
      add(productKey, sseUrl)
      if (LEGACY_KEY != productKey) {
        if (writeLegacy()) {
          add(LEGACY_KEY, sseUrl)
        }
        else {
          remove(LEGACY_KEY)
        }
      }
    }
  }

  override fun getSSEConfig(): ServerConfig = ClaudeCodeSSEConfig(type = "sse", url = sseUrl)
}