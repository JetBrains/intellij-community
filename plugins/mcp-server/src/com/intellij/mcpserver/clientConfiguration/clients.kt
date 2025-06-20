@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.clientConfiguration

import com.intellij.mcpserver.createStdioMcpServerCommandLine
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PORT
import com.intellij.mcpserver.stdio.main
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.reflect.jvm.javaMethod

open class McpClient(
  val name: String,
  val configPath: Path,
) {

  override fun toString(): String {
    return name
  }

  private val json = Json {
    allowComments = true
    allowTrailingComma = true
    prettyPrint = true
    prettyPrintIndent = "  "
    classDiscriminatorMode = ClassDiscriminatorMode.NONE
  }

  open fun isConfigured(): Boolean = true
  open fun configure() = configureStdIO()

  open fun mcpServersKey() = "mcpServers"

  protected fun isStdIOConfigured(): Boolean {
    runCatching {
      json.decodeFromStream<McpServers>(configPath.inputStream()).mcpServers.forEach { mcpServer ->
        if (mcpServer.value.command?.contains("java") == true && mcpServer.value.env?.contains(::IJ_MCP_SERVER_PORT.name) == true &&
            mcpServer.value.args?.contains(::main.javaMethod!!.declaringClass.name) == true) {
          return true
        }
      }
    }
      .getOrElse { return true }
    return false
  }

  protected fun isSSEConfigured(): Boolean {
    runCatching {
      json.decodeFromStream<McpServers>(configPath.inputStream()).mcpServers.forEach { mcpServer ->
        if (mcpServer.value.url?.matches(Regex("""http://localhost:(\d+)/sse""")) == true) {
          return true
        }
      }
    }
      .getOrElse { return true }
    return false
  }

  protected fun updateServerConfig(serverEntry: ServerConfig) {
    val existingJson = if (configPath.exists()) {
      json.decodeFromStream<JsonObject>(configPath.inputStream())
    }
    else {
      buildJsonObject {}
    }

    val existingServers = existingJson[mcpServersKey()]?.jsonObject ?: buildJsonObject {}

    val updatedServers = buildJsonObject {
      existingServers.forEach { (key, value) ->
        put(key, value)
      }
      put("jetbrains", json.encodeToJsonElement(serverEntry))
    }

    val finalJson = buildJsonObject {
      existingJson.forEach { (key, value) ->
        if (key != mcpServersKey()) {
          put(key, value)
        }
      }
      put(mcpServersKey(), updatedServers)
    }

    configPath.parent?.createParentDirectories()
    configPath.outputStream().use { outputStream ->
      json.encodeToStream(finalJson, outputStream)
    }
  }

  protected fun getSSEUrl() = "http://localhost:${McpServerService.getInstance().port}/sse"

  protected fun configureStdIO() {
    val cmd = createStdioMcpServerCommandLine(McpServerService.getInstance().port, null)
    val serverEntry = STDIOServerConfig(command = cmd.exePath, args = cmd.parametersList.parameters, env = cmd.environment)
    updateServerConfig(serverEntry)
  }
}

class ClaudeMcpClient(name: String, configPath: Path) : McpClient(name, configPath) {
  override fun isConfigured(): Boolean = isStdIOConfigured()
}

class CursorClient(name: String, configPath: Path) : McpClient(name, configPath) {
  override fun isConfigured(): Boolean = isStdIOConfigured() || isSSEConfigured()

  private fun configureSSE() {
    val serverEntry = CursorSSEConfig(url = getSSEUrl())
    updateServerConfig(serverEntry)
  }

  override fun configure() = configureSSE()
}

class WindsurfClient(name: String, configPath: Path) : McpClient(name, configPath) {
  override fun isConfigured(): Boolean = isStdIOConfigured() || isSSEConfigured()
  private fun configureSSE() {
    val serverEntry = WindsurfSSEConfig(serverUrl = getSSEUrl())
    updateServerConfig(serverEntry)
  }

  override fun configure() = configureSSE()
}