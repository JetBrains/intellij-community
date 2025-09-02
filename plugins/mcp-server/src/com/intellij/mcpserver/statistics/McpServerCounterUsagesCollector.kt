package com.intellij.mcpserver.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.mcpserver.McpToolset
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.resettableLazy
import kotlinx.coroutines.CoroutineScope

internal object McpServerCounterUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("mcpserver.events", 1)

  private val MCP_CALL: EventId1<String> = GROUP.registerEvent("mcp.tool.call", EventFields.StringValidatedByCustomRule<McpToolNameValidator>("tool_name"))

  override fun getGroup(): EventLogGroup = GROUP

  fun reportMcpCall(mcpToolDescriptor: McpToolDescriptor) = MCP_CALL.log(mcpToolDescriptor.name)

  internal class McpToolNameValidator : CustomValidationRule() {
    private val valueMap = resettableLazy { McpToolsProvider.EP.extensionList.associateWith { ext -> ext.getTools().map { it.descriptor.name } } }

    @Service(Service.Level.APP)
    private class ScopeHolder(val cs: CoroutineScope)

    init {
      McpToolsProvider.EP.addExtensionPointListener(service<ScopeHolder>().cs, object : ExtensionPointListener<McpToolsProvider> {
        override fun extensionAdded(extension: McpToolsProvider, pluginDescriptor: PluginDescriptor) = valueMap.reset()
        override fun extensionRemoved(extension: McpToolsProvider, pluginDescriptor: PluginDescriptor) = valueMap.reset()
      })

      McpToolset.EP.addExtensionPointListener(service<ScopeHolder>().cs, object : ExtensionPointListener<McpToolset> {
        override fun extensionAdded(extension: McpToolset, pluginDescriptor: PluginDescriptor) = valueMap.reset()
        override fun extensionRemoved(extension: McpToolset, pluginDescriptor: PluginDescriptor) = valueMap.reset()
      })
    }

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
      for ((ext, tools) in valueMap.value) {
        if (tools.contains(data))  {
          return if (getPluginInfo(ext.javaClass).isSafeToReport()) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
        }
      }
      return ValidationResultType.REJECTED
    }

    override fun getRuleId(): String = "tool_name_validator_id"
  }
}