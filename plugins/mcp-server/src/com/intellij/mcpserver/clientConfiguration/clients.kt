@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.clientConfiguration

import com.intellij.mcpserver.createStdioMcpServerCommandLine
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PORT
import com.intellij.mcpserver.stdio.main
import com.intellij.openapi.util.NlsContexts
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.reflect.jvm.javaMethod

open class McpClient(
  @NlsContexts.BorderTitle val name: String,
  val configPath: Path,
) {

  override fun toString(): String {
    return name
  }

  val json by lazy {
    Json {
      allowComments = true
      allowTrailingComma = true
      prettyPrint = true
      prettyPrintIndent = "  "
      classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }
  }

  protected val sseUrl by lazy { "http://localhost:${McpServerService.getInstance().port}/sse" }

  open fun isConfigured(): Boolean = true
  open fun configure() = configureStdIO()

  open fun isSSESupported(): Boolean = true

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

  fun isPortCorrect(): Boolean {
    val currentPort = McpServerService.getInstance().port
    runCatching {
      json.decodeFromStream<McpServers>(configPath.inputStream()).mcpServers.forEach { mcpServer ->
        mcpServer.value.url?.let { url ->
          val urlRegex = Regex("""http://localhost:(\d+)/sse""")
          val matchResult = urlRegex.find(url)
          if (matchResult != null) {
            val configuredPort = matchResult.groupValues[1].toIntOrNull()
            return configuredPort == currentPort
          }
        }

        if (mcpServer.value.command?.contains("java") == true &&
            mcpServer.value.args?.contains(::main.javaMethod!!.declaringClass.name) == true) {
          val configuredPort = mcpServer.value.env?.get(::IJ_MCP_SERVER_PORT.name)?.toIntOrNull()
          return configuredPort == currentPort
        }
      }
    }.getOrElse { return true }

    return true
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


  fun configureStdIO() = updateServerConfig(getStdioConfig())

  open fun getSSEConfig(): ServerConfig? = null

  fun getStdioConfig(): ServerConfig {
    val cmd = createStdioMcpServerCommandLine(McpServerService.getInstance().port, null)
    return STDIOServerConfig(command = cmd.exePath, args = cmd.parametersList.parameters, env = cmd.environment)
  }
}

class ClaudeMcpClient(name: String, configPath: Path) : McpClient(name, configPath) {
  override fun isConfigured(): Boolean = isStdIOConfigured()
  override fun isSSESupported(): Boolean = false
}

class CursorClient(name: String, configPath: Path) : McpClient(name, configPath) {
  override fun isConfigured(): Boolean = isStdIOConfigured() || isSSEConfigured()
  override fun getSSEConfig(): ServerConfig = CursorSSEConfig(url = sseUrl)
  override fun configure() = updateServerConfig(getSSEConfig())
}

class WindsurfClient(name: String, configPath: Path) : McpClient(name, configPath) {
  override fun isConfigured(): Boolean = isStdIOConfigured() || isSSEConfigured()
  override fun getSSEConfig(): ServerConfig = WindsurfSSEConfig(serverUrl = sseUrl)
  override fun configure() = updateServerConfig(getSSEConfig())
}