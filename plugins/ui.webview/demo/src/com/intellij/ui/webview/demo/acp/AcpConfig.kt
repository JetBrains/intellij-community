// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo.acp

import com.intellij.openapi.util.SystemInfoRt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

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
  val iconResourcePath: String? = null,
)

internal object AcpConfig {
  private const val JUNIE_AGENT_ID = "junie"
  private const val JUNIE_AGENT_NAME = "Junie"
  private const val JUNIE_AGENT_ICON_RESOURCE_PATH = "webview/views/acp-chat/assets/acpChatJunie.svg"
  private const val DEFAULT_CONFIG_TEXT = """{
  "agent_servers": {}
}
"""

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  fun configPath(): Path = Path.of(System.getProperty("user.home"), ".jetbrains", "acp.json")

  fun ensureConfigFile(): Path {
    val path = configPath()
    if (!path.exists()) {
      path.parent?.createDirectories()
      path.writeText(DEFAULT_CONFIG_TEXT)
    }
    return path
  }

  fun loadAgents(): List<AcpAgent> = listOf(createJunieAgent()) + runCatching { loadConfiguredAgents() }.getOrDefault(emptyList())

  private fun loadConfiguredAgents(): List<AcpAgent> {
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

  private fun createJunieAgent(): AcpAgent {
    val junieExecutableName = if (SystemInfoRt.isWindows) "junie.bat" else "junie"
    val junieExecutable = Path.of(System.getProperty("user.home"), ".local", "bin", junieExecutableName).toString()
    return AcpAgent(
      id = JUNIE_AGENT_ID,
      name = JUNIE_AGENT_NAME,
      command = junieExecutable,
      args = listOf("--acp=true"),
      env = emptyMap(),
      iconResourcePath = JUNIE_AGENT_ICON_RESOURCE_PATH,
    )
  }

  private fun expandHome(value: String): String {
    val userHome = System.getProperty("user.home")
    return when {
      value == "~" -> userHome
      value.startsWith("~/") || value.startsWith("~\\") -> Path.of(userHome, value.substring(2)).toString()
      else -> value
    }
  }
}
