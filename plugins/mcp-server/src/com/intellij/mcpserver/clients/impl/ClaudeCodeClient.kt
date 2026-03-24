package com.intellij.mcpserver.clients.impl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ClaudeCodeNetworkConfig
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
    val network = isSSEOrStreamConfigured() ?: return null
    return stdio || network
  }

  override suspend fun configure(config: ServerConfig) {
    fun remove(name: String) {
      val removeCmd = GeneralCommandLine().withExePath("claude").withParameters("mcp", "remove", "--scope", "user", name)
      val output = ExecUtil.execAndGetOutput(removeCmd, 1000)
      logger.trace { "Claude remove mcp stdout: ${output.stdout}" }
      logger.trace { "Claude remove mcp stderr: ${output.stderr}" }
    }

    fun add(name: String, url: String, transportType: String) {
      val addCmd = GeneralCommandLine()
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withExePath("claude")
        .withParameters("mcp", "add", "--scope", "user", "--transport", transportType, name, url)
      val out = ExecUtil.execAndGetOutput(addCmd, 1000)
      if (out.exitCode != 0) {
        throw McpClientConfigurationException(out.stdout)
      }
    }

    val productKey = productSpecificServerKey()
    remove(productKey)
    if (config !is ClaudeCodeNetworkConfig) {
      throw IllegalArgumentException("Unexpected config type: ${config::class.java}")
    }
    add(productKey, config.url, config.type)
    if (LEGACY_KEY != productKey) {
      if (writeLegacy()) {
        add(LEGACY_KEY, config.url, config.type)
      }
      else {
        remove(LEGACY_KEY)
      }
    }
  }

  override suspend fun getSSEConfig(): ServerConfig = ClaudeCodeNetworkConfig(type = "sse", url = sseUrl)

  override suspend fun getStreamableHttpConfig(): ServerConfig = ClaudeCodeNetworkConfig(type = "http", url = streamableHttpUrl)
}