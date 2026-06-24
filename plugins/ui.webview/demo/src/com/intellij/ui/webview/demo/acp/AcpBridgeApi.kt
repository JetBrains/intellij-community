// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo.acp

import com.intellij.ui.webview.api.WebViewApiId
import com.intellij.ui.webview.api.WebViewCallable
import com.intellij.ui.webview.api.WebViewImplementable
import kotlinx.serialization.Serializable

/**
 * Host API implemented in Kotlin and called from the page (TS -> Kotlin).
 * The page drives the agent lifecycle; Kotlin only spawns the process and pipes its stdio.
 */
internal interface AcpBridgeHostApi : WebViewImplementable {
  suspend fun listAgents(): AgentListDto

  suspend fun startAgent(params: StartAgentRequest): StartAgentResult

  suspend fun sendStdin(params: LineDto)

  suspend fun stopAgent()

  companion object {
    val ID: WebViewApiId<AcpBridgeHostApi> = WebViewApiId.of("acp.bridge")
  }
}

/**
 * Page API implemented in TS and called from Kotlin (Kotlin -> TS notifications).
 * Streams the agent's raw ndjson stdout lines to the webview, where the ACP TS SDK decodes them.
 */
internal interface AcpBridgePageApi : WebViewCallable {
  fun onAgentStdout(params: LineDto)

  fun onAgentExit(params: ExitDto)

  companion object {
    val ID: WebViewApiId<AcpBridgePageApi> = WebViewApiId.of("acp.bridge")
  }
}

@Serializable
internal data class AgentDto(val id: String, val name: String)

@Serializable
internal data class AgentListDto(val agents: List<AgentDto>)

@Serializable
internal data class StartAgentRequest(val agentId: String, val extraEnv: Map<String, String> = emptyMap())

@Serializable
internal data class StartAgentResult(val ok: Boolean, val cwd: String? = null, val error: String? = null)

@Serializable
internal data class LineDto(val line: String)

@Serializable
internal data class ExitDto(val code: Int? = null)
