package com.intellij.mcpserver.statistics;

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.mcpserver.impl.McpServerService

internal class McpServerApplicationUsagesCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("mcpserver", 1)
  private val MCP_RUNNING = GROUP.registerEvent("mcp.running", EventFields.Enabled)

  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    return setOf(MCP_RUNNING.metric(McpServerService.getInstanceAsync().isRunning))
  }
}
