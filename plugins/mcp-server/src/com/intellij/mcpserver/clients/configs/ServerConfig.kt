@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.clients.configs

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
sealed class ServerConfig

@Serializable
class CursorNetworkConfig(val url: String, val type: String, val headers: Map<String, String>? = null) : ServerConfig()

@Serializable
class WindsurfNetworkConfig(val serverUrl: String, val type: String, val headers: Map<String, String>? = null) : ServerConfig()

@Serializable
class VSCodeNetworkConfig(val url: String, val type: String, val headers: Map<String, String>? = null) : ServerConfig()

@Serializable
class ClaudeCodeNetworkConfig(val url: String, val type: String, val headers: Map<String, String>? = null) : ServerConfig()

@Serializable
class JunieNetworkConfig(val url: String, val type: String, val headers: Map<String, String>? = null) : ServerConfig()

@Serializable
class AirNetworkConfig(val url: String, val type: String, val headers: Map<String, String>? = null) : ServerConfig()

@Serializable
class GitHubCopilotNetworkConfig(val url: String, val type: String, val headers: Map<String, String>? = null) : ServerConfig()

@Serializable
class CodexStreamableHttpConfig(val url: String, val headers: Map<String, String>? = null) : ServerConfig()

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
