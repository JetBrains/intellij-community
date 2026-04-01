package com.intellij.mcpserver.clients.impl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ClaudeCodeNetworkConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.spawnProcess
import java.nio.file.Files
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

  private suspend fun resolveBinaryPath(): String {
    if (SystemInfo.isWindows) {
      val localEel = LocalEelDescriptor.toEelApi()
      try {
        // checking if claude is available via PATH
        localEel.exec.spawnProcess("claude", "-v").eelIt()
        return "claude"
      }
      catch (err: ExecuteProcessException) {
        // claude is not available via PATH; let's look into a directory where it is likely installed
        val pathInBin = localEel.userInfo.home.resolve(".local").resolve("bin").resolve("claude.exe")
        if (Files.exists(pathInBin.asNioPath())) {
          return pathInBin.toString()
        }
        return "claude"
      }
    } else {
      // hoping to resolve claude in PATH
      return "claude"
    }
  }

  override suspend fun configure(config: ServerConfig) {
    val exeName = resolveBinaryPath()
    fun remove(name: String) {
      val removeCmd = GeneralCommandLine().withExePath(exeName).withParameters("mcp", "remove", "--scope", "user", name)
      val output = ExecUtil.execAndGetOutput(removeCmd, 1000)
      logger.trace { "Claude remove mcp stdout: ${output.stdout}" }
      logger.trace { "Claude remove mcp stderr: ${output.stderr}" }
    }

    fun add(name: String, url: String, transportType: String) {
      val addCmd = GeneralCommandLine()
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withExePath(exeName)
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