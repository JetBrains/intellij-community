@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.clientConfiguration

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonNames

// used for writing config
@Serializable
sealed class ServerConfig

@Serializable
class CursorSSEConfig(val url: String) : ServerConfig()

@Serializable
class WindsurfSSEConfig(val serverUrl: String) : ServerConfig()

@Serializable
class STDIOServerConfig(
  val command: String? = null,
  val args: List<String>? = null,
  val env: Map<String, String>? = null,
) : ServerConfig()

// used for reading existing configs

@Serializable
class ExistingConfig(
  val command: String? = null,
  val args: List<String>? = null,
  val env: Map<String, String>? = null,
  @JsonNames("url", "serverUrl")
  val url: String? = null,
)

@JsonIgnoreUnknownKeys
@Serializable
data class McpServers(
  @JsonNames("servers", "mcpServers")
  val mcpServers: Map<String, ExistingConfig>,
)