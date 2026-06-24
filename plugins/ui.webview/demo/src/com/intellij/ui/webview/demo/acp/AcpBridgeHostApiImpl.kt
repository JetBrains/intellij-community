// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo.acp

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AcpBridgeHostApiImpl(
  private val bridge: AcpProcessBridge,
) : AcpBridgeHostApi {
  override suspend fun listAgents(): AgentListDto = withContext(Dispatchers.IO) {
    val agents = runCatching { AcpConfig.loadAgents() }
      .onFailure { LOG.warn("Failed to read acp.json at ${AcpConfig.configPath()}", it) }
      .getOrDefault(emptyList())
    AgentListDto(agents.map { AgentDto(id = it.id, name = it.name) })
  }

  override suspend fun startAgent(params: StartAgentRequest): StartAgentResult = withContext(Dispatchers.IO) {
    val agent = runCatching { AcpConfig.loadAgents() }.getOrDefault(emptyList()).find { it.id == params.agentId }
               ?: return@withContext StartAgentResult(ok = false, error = "Agent not found: ${params.agentId}")
    try {
      val cwd = bridge.start(agent, params.extraEnv)
      StartAgentResult(ok = true, cwd = cwd)
    }
    catch (t: Throwable) {
      LOG.warn("Failed to start ACP agent '${params.agentId}'", t)
      StartAgentResult(ok = false, error = t.message ?: t.toString())
    }
  }

  override suspend fun sendStdin(params: LineDto) {
    bridge.send(params.line)
  }

  override suspend fun stopAgent() {
    bridge.stop()
  }

  private companion object {
    private val LOG = logger<AcpBridgeHostApiImpl>()
  }
}
