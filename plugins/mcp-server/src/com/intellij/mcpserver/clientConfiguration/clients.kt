@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.clientConfiguration

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.createStdioMcpServerCommandLine
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PORT
import com.intellij.mcpserver.stdio.main
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.reflect.jvm.javaMethod

enum class MCPClientNames(val displayName: String){
  VS_CODE_PROJECT("VSCode (Project)"),
  VS_CODE_GLOBAL("VSCode"),
  CLAUDE_APP_GLOBAL("Claude App"),
  WINDSURF_PROJECT("Windsurf (Project)"),
  WINDSURF_GLOBAL("Windsurf"),
  CURSOR_PROJECT("Cursor (Project)"),
  CURSOR_GLOBAL("Cursor"),
  CLAUDE_CODE_PROJECT("Claude Code (Project)"),
  CLAUDE_CODE_GLOBAL("Claude Code"),
}

open class McpClient(
  @NlsContexts.BorderTitle val name: MCPClientNames,
  val configPath: Path,
) {

  companion object {
    private val SSE_URL_REGEX = Regex("""http://localhost:(\d+)/sse""")
    protected const val JETBRAINS_SERVER_KEY = "jetbrains"

    val json by lazy {
      Json {
        allowComments = true
        allowTrailingComma = true
        prettyPrint = true
        prettyPrintIndent = "  "
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
      }
    }
  }

  override fun toString(): String {
    return name.displayName
  }

  protected val sseUrl by lazy { "http://localhost:${McpServerService.getInstance().port}/sse" }

  open fun isConfigured(): Boolean? = true
  open fun mcpServersKey() = "mcpServers"
  open fun configure() = updateServerConfig(getConfig())
  fun getConfig(): ServerConfig = getSSEConfig() ?: getStdioConfig()

  protected open fun getSSEConfig(): ServerConfig? = null

  private fun getStdioConfig(): ServerConfig {
    val cmd = createStdioMcpServerCommandLine(McpServerService.getInstance().port, null)
    return STDIOServerConfig(command = cmd.exePath, args = cmd.parametersList.parameters, env = cmd.environment)
  }

  protected open fun readMcpServers(): Map<String, ExistingConfig>? {
    return runCatching {
      if (!configPath.exists()) return null
      json.decodeFromStream<McpServers>(configPath.inputStream()).mcpServers
    }.getOrNull()
  }

  protected fun isStdIOConfigured(): Boolean? {
    val mcpServers = readMcpServers()
    if (mcpServers?.isEmpty() ?: true) return null
    return mcpServers.any { (_, serverConfig) ->
      serverConfig.command?.contains("java") == true &&
      serverConfig.env?.containsKey(::IJ_MCP_SERVER_PORT.name) == true &&
      serverConfig.args?.contains(::main.javaMethod!!.declaringClass.name) == true
    }
  }

  protected fun isSSEConfigured(): Boolean? {
    val mcpServers = readMcpServers()
    if (mcpServers?.isEmpty() ?: true) return null
    return mcpServers.any { (_, serverConfig) ->
      serverConfig.url?.matches(SSE_URL_REGEX) == true
    } ?: true
  }

  fun isPortCorrect(): Boolean {
    if (!McpServerService.getInstance().isRunning) return true
    val currentPort = McpServerService.getInstance().port
    val mcpServers = readMcpServers()
    if (mcpServers?.isEmpty() ?: true) return true
    return readMcpServers()?.any { (_, serverConfig) ->
      isPortMatching(serverConfig, currentPort)
    } == true
  }

  private fun isPortMatching(serverConfig: ExistingConfig, targetPort: Int): Boolean {
    serverConfig.url?.let { url ->
      val matchResult = SSE_URL_REGEX.find(url)
      matchResult?.groupValues?.get(1)?.toIntOrNull()?.let { configuredPort ->
        return configuredPort == targetPort
      }
      return false
    }

    if (serverConfig.command?.contains("java") == true &&
        serverConfig.env?.containsKey(::IJ_MCP_SERVER_PORT.name) == true &&
        serverConfig.args?.contains(::main.javaMethod!!.declaringClass.name) == true) {
      val configuredPort = serverConfig.env[::IJ_MCP_SERVER_PORT.name]?.toIntOrNull()
      return configuredPort == targetPort
    }
    return true
  }

  protected fun updateServerConfig(serverEntry: ServerConfig) {
    val existingConfig = readExistingConfig()
    val updatedConfig = buildUpdatedConfig(existingConfig, serverEntry)
    writeConfigToFile(updatedConfig)
  }

  private fun readExistingConfig(): JsonObject {
    return if (configPath.exists()) {
      runCatching {
        json.decodeFromStream<JsonObject>(configPath.inputStream())
      }.getOrElse { buildJsonObject {} }
    }
    else {
      buildJsonObject {}
    }
  }

  protected open fun buildUpdatedConfig(existingConfig: JsonObject, serverEntry: ServerConfig): JsonObject {
    val existingServers = existingConfig[mcpServersKey()]?.jsonObject ?: buildJsonObject {}

    val updatedServers = buildJsonObject {
      existingServers.forEach { (key, value) -> put(key, value) }
      put(JETBRAINS_SERVER_KEY, json.encodeToJsonElement(serverEntry))
    }

    return buildJsonObject {
      existingConfig.forEach { (key, value) ->
        if (key != mcpServersKey()) {
          put(key, value)
        }
      }
      put(mcpServersKey(), updatedServers)
    }
  }

  private fun writeConfigToFile(config: JsonObject) {
    configPath.parent?.createParentDirectories()
    configPath.outputStream().use { outputStream ->
      json.encodeToStream(config, outputStream)
    }
  }
}

class ClaudeCodeMcpClient(configPath: Path) : McpClient(MCPClientNames.CLAUDE_CODE_GLOBAL, configPath) {
  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val sse = isSSEConfigured() ?: return null
    return stdio || sse
  }

  override fun configure() {
    runWithModalProgressBlocking(ModalTaskOwner.guess(), McpServerBundle.message("autoconfigure.progress.title"), TaskCancellation.nonCancellable()) {
      val claudeAddMCP = GeneralCommandLine()
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withExePath("claude")
        .withParameters("mcp", "add", "--scope", "user", "--transport", "sse", JETBRAINS_SERVER_KEY, sseUrl)

      val execAndGetOutput = ExecUtil.execAndGetOutput(claudeAddMCP, 1000)

      if (execAndGetOutput.exitCode != 0) {
        throw Exception("Claude failed with exit code ${execAndGetOutput.exitCode}: ${execAndGetOutput.stderr}")
      }
    }

  }
  override fun getSSEConfig(): ServerConfig = ClaudeCodeSSEConfig(type="sse", url = sseUrl)
}

class ClaudeMcpClient(configPath: Path) : McpClient(MCPClientNames.CLAUDE_APP_GLOBAL, configPath) {
  override fun isConfigured(): Boolean? = isStdIOConfigured()
}

class CursorClient(configPath: Path) : McpClient(MCPClientNames.CURSOR_GLOBAL, configPath) {
  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val sse = isSSEConfigured() ?: return null
    return stdio || sse
  }
  override fun getSSEConfig(): ServerConfig = CursorSSEConfig(url = sseUrl)
}

class WindsurfClient(configPath: Path) : McpClient(MCPClientNames.WINDSURF_GLOBAL, configPath) {
  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val sse = isSSEConfigured() ?: return null
    return stdio || sse
  }
  override fun getSSEConfig(): ServerConfig = WindsurfSSEConfig(serverUrl = sseUrl)
}

class VSCodeClient(configPath: Path) : McpClient(MCPClientNames.VS_CODE_GLOBAL, configPath) {
  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val sse = isSSEConfigured() ?: return null
    return stdio || sse
  }
  override fun getSSEConfig(): ServerConfig = VSCodeSSEConfig(url = sseUrl, type = "sse")
  
  override fun readMcpServers(): Map<String, ExistingConfig>? {
    return runCatching {
      if (!configPath.exists()) return null
      json.decodeFromStream<VSCodeConfig>(configPath.inputStream()).servers ?: emptyMap()
    }.getOrNull()
  }
  
  override fun buildUpdatedConfig(existingConfig: JsonObject, serverEntry: ServerConfig): JsonObject {
    val existingServers = existingConfig["servers"]?.jsonObject ?: buildJsonObject {}

    return buildJsonObject {
      put("servers", buildJsonObject {
        existingServers.forEach { (key, value) -> put(key, value) }
        put(JETBRAINS_SERVER_KEY, json.encodeToJsonElement<ServerConfig>(serverEntry))
      })
    }
  }
}