@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.clients.configs

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
sealed class ServerConfig

@Serializable
class CursorSSEConfig(val url: String) : ServerConfig()

@Serializable
class WindsurfSSEConfig(val serverUrl: String) : ServerConfig()

@Serializable
class VSCodeSSEConfig(val url: String, val type: String) : ServerConfig()

@Serializable
class ClaudeCodeSSEConfig(val url: String, val type: String) : ServerConfig()

@Serializable
class CodexStreamableHttpConfig(val url: String) : ServerConfig()

@Serializable
class STDIOServerConfig(
  val command: String? = null,
  val args: List<String>? = null,
  val env: Map<String, String>? = null,
) : ServerConfig()

@JsonIgnoreUnknownKeys
@Serializable
data class VSCodeConfig(
  val servers: Map<String, ExistingConfig>? = null
)
