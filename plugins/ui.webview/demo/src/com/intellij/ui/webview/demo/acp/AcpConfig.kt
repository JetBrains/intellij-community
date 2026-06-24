// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo.acp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Minimal model of `~/.jetbrains/acp.json`, copied (not depended on) from the ACP plugin so the demo stays self-contained.
 * Only the `agent_servers` map is needed to list and spawn local ACP agents.
 */
@Serializable
internal data class AcpConfiguration(
  @SerialName("agent_servers")
  val agentServers: Map<String, AgentServerConfig> = emptyMap(),
)

@Serializable
internal data class AgentServerConfig(
  val command: String,
  val args: List<String> = emptyList(),
  val env: Map<String, String> = emptyMap(),
)

/** A resolved, launchable agent entry from `acp.json`. */
internal data class AcpAgent(
  val id: String,
  val name: String,
  val command: String,
  val args: List<String>,
  val env: Map<String, String>,
)

internal object AcpConfig {
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  fun configPath(): Path = Path.of(System.getProperty("user.home"), ".jetbrains", "acp.json")

  fun loadAgents(): List<AcpAgent> {
    val path = configPath()
    if (!path.exists()) return emptyList()
    val text = path.readText()
    if (text.isBlank()) return emptyList()
    val config = json.decodeFromString<AcpConfiguration>(text)
    return config.agentServers.map { (name, cfg) ->
      AcpAgent(
        id = name,
        name = name,
        command = expandHome(cfg.command),
        args = cfg.args.map(::expandHome),
        env = cfg.env,
      )
    }
  }

  private fun expandHome(value: String): String =
    if (value == "~" || value.startsWith("~/")) System.getProperty("user.home") + value.substring(1) else value
}
