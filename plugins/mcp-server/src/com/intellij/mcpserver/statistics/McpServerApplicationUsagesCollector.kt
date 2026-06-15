package com.intellij.mcpserver.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.impl.McpClientDetector
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import com.intellij.mcpserver.settings.McpToolFilterSettings

internal class McpServerApplicationUsagesCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("mcpserver", 8)
  private val MCP_RUNNING = GROUP.registerEvent("mcp.running", EventFields.Enabled)
  private val MCP_BRAVE_MODE_ENABLED = GROUP.registerEvent("mcp.brave.mode.enabled", EventFields.Enabled)
  private val MCP_GLOBAL_CLIENTS = GROUP.registerEvent("mcp.global.clients",
                                                       EventFields.Enum<McpClientInfo.Name>("client_type") { it.baseName },
                                                       EventFields.Boolean("is_configured"),
                                                       EventFields.Boolean("has_port_mismatch"))
  private val MCP_ROUTER_MODE = GROUP.registerEvent(
    "mcp.router.mode",
    EventFields.Enum(
      "mode",
      McpSessionInvocationMode::class.java,
      "Global MCP invocation mode (whether the universal router is used to dispatch tool calls). Gates the per-tool router_only flag reported by mcp.tool.state — when DIRECT, router_only has no runtime effect.",
    ),
  )
  private val MCP_TOOL_STATE = GROUP.registerEvent(
    "mcp.tool.state",
    EventFields.StringValidatedByCustomRule<McpServerCounterUsagesCollector.McpToolNameValidator>("tool_name"),
    EventFields.Boolean("enabled",
                        "Whether the MCP tool is enabled (exposed to clients) in the current configuration"),
    EventFields.Boolean("router_only",
                        "Whether the MCP tool is exposed only via the universal router (on-demand) instead of being callable directly"),
  )

  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    val settings = McpServerSettings.getInstance()
    val mcpServerService = McpServerService.getInstanceAsync()
    val metrics = mutableSetOf<MetricEvent>()

    metrics.add(MCP_RUNNING.metric(mcpServerService.isRunning))
    metrics.add(MCP_BRAVE_MODE_ENABLED.metric(settings.state.enableBraveMode))
    metrics.add(MCP_ROUTER_MODE.metric(McpToolFilterSettings.getInstance().invocationMode))

    McpClientDetector.detectGlobalMcpClients().forEach { client ->
      metrics.add(MCP_GLOBAL_CLIENTS.metric(client.mcpClientInfo.name, client.isConfigured() ?: false, !client.isPortCorrect()))
    }

    metrics.addAll(collectToolStateMetrics(mcpServerService))
    return metrics
  }

  private fun collectToolStateMetrics(mcpServerService: McpServerService): List<MetricEvent> {
    val disallowListSettings = McpToolDisallowListSettings.getInstance()
    return mcpServerService.getAllMcpTools().map { tool ->
      val state = disallowListSettings.toolStateFor(tool)
      MCP_TOOL_STATE.metric(tool.descriptor.name, state.enabled, state.routerOnly)
    }
  }
}
