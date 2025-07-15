package com.intellij.mcpserver.statistics;

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.mcpserver.clientConfiguration.MCPClientNames
import com.intellij.mcpserver.impl.McpClientDetector
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpServerSettings

internal class McpServerApplicationUsagesCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("mcpserver", 3)
  private val MCP_RUNNING = GROUP.registerEvent("mcp.running", EventFields.Enabled)
  private val MCP_BRAVE_MODE_ENABLED = GROUP.registerEvent("mcp.brave.mode.enabled", EventFields.Enabled)
  private val MCP_GLOBAL_CLIENTS = GROUP.registerEvent("mcp.global.clients",
                                                       EventFields.Enum<MCPClientNames>("client_type") { it.displayName },
                                                       EventFields.Boolean("is_configured"),
                                                       EventFields.Boolean("has_port_mismatch"))

  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    val settings = McpServerSettings.getInstance()
    val metrics = mutableSetOf<MetricEvent>()
    
    metrics.add(MCP_RUNNING.metric(McpServerService.getInstanceAsync().isRunning))
    metrics.add(MCP_BRAVE_MODE_ENABLED.metric(settings.state.enableBraveMode))

    McpClientDetector.detectGlobalMcpClients().forEach { client ->
      metrics.add(MCP_GLOBAL_CLIENTS.metric(client.name, client.isConfigured() ?: false, !client.isPortCorrect()))
    }
    return metrics
  }
}
