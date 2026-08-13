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
  // v10: mcp.tools.exposed, the aggregate cost of what the IDE exposes.
  private val GROUP = EventLogGroup("mcpserver", 10)
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

  /**
   * The aggregate cost of the exposed tool set. `mcp.tool.state` already reports one row per tool, but an analysis of
   * how much context the IDE spends on tool descriptions cannot be built by summing hundreds of rows per report.
   */
  private val TOOLS_TOTAL = EventFields.Int("tools_total", "Number of MCP tools the IDE knows about, enabled or not")
  private val TOOLS_ENABLED = EventFields.Int("tools_enabled", "Number of tools exposed to clients in the current configuration")
  private val TOOLS_ROUTER_ONLY = EventFields.Int("tools_router_only", "Number of tools reachable only through the universal router")
  private val TOOLS_DISABLED = EventFields.Int("tools_disabled", "Number of tools not exposed to clients")
  private val DESCRIPTION_BYTES = EventFields.RoundedInt(
    "description_bytes",
    "Total length of the descriptions of the exposed tools, rounded. This is the context every session pays for " +
    "before a single tool is called",
  )

  private val MCP_TOOLS_EXPOSED = GROUP.registerVarargEvent(
    "mcp.tools.exposed",
    TOOLS_TOTAL, TOOLS_ENABLED, TOOLS_ROUTER_ONLY, TOOLS_DISABLED, DESCRIPTION_BYTES,
  )

  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    val settings = McpServerSettings.getInstance()
    val mcpServerService = McpServerService.getInstanceAsync()
    val metrics = mutableSetOf<MetricEvent>()

    metrics.add(MCP_RUNNING.metric(mcpServerService.isRunning))
    metrics.add(MCP_BRAVE_MODE_ENABLED.metric(settings.enableBraveMode))
    metrics.add(MCP_ROUTER_MODE.metric(McpToolFilterSettings.getInstance().invocationMode))

    McpClientDetector.detectGlobalMcpClients().forEach { client ->
      metrics.add(MCP_GLOBAL_CLIENTS.metric(client.mcpClientInfo.name, client.isConfigured() ?: false, !client.isPortCorrect()))
    }

    metrics.addAll(collectToolStateMetrics(mcpServerService))
    metrics.add(collectExposedToolsMetric(mcpServerService))
    return metrics
  }

  private fun collectExposedToolsMetric(mcpServerService: McpServerService): MetricEvent {
    val disallowListSettings = McpToolDisallowListSettings.getInstance()
    var enabled = 0
    var routerOnly = 0
    var descriptionBytes = 0
    val tools = mcpServerService.getAllMcpTools()
    for (tool in tools) {
      val state = disallowListSettings.toolStateFor(tool)
      if (!state.enabled) continue
      enabled++
      if (state.routerOnly) routerOnly++
      descriptionBytes += tool.descriptor.description.length
    }
    return MCP_TOOLS_EXPOSED.metric(
      TOOLS_TOTAL.with(tools.size),
      TOOLS_ENABLED.with(enabled),
      TOOLS_ROUTER_ONLY.with(routerOnly),
      TOOLS_DISABLED.with(tools.size - enabled),
      DESCRIPTION_BYTES.with(descriptionBytes),
    )
  }

  private fun collectToolStateMetrics(mcpServerService: McpServerService): List<MetricEvent> {
    val disallowListSettings = McpToolDisallowListSettings.getInstance()
    return mcpServerService.getAllMcpTools().map { tool ->
      val state = disallowListSettings.toolStateFor(tool)
      MCP_TOOL_STATE.metric(tool.descriptor.name, state.enabled, state.routerOnly)
    }
  }
}
