@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.clients.configs

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonNames

@Serializable
@JsonIgnoreUnknownKeys
class ExistingConfig(
  val command: String? = null,
  val args: List<String>? = null,
  val env: Map<String, String>? = null,
  @JsonNames("url", "serverUrl")
  val url: String? = null,
  val type: String? = null,
)