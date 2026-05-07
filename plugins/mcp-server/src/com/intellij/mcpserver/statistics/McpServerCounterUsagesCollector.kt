package com.intellij.mcpserver.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.VarargEventId
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
import com.intellij.util.resettableLazy
import kotlinx.coroutines.CoroutineScope

internal object McpServerCounterUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("mcpserver.events", 2)

  private val MCP_TOOL_CALL_EVENT: EventId1<String> = GROUP.registerEvent(
    "mcp.tool.call",
    EventFields.StringValidatedByCustomRule<McpToolNameValidator>("tool_name"),
  )

  private val DISPATCHED_TOOL_NAME = EventFields.StringValidatedByCustomRule<McpToolNameValidator>("dispatched_tool_name")
  private val ARG_COUNT = EventFields.Int("arg_count")
  private val DISPATCHED_TOOL_FOUND = EventFields.Boolean("dispatched_tool_found")
  private val SUCCESS = EventFields.Boolean("success")

  private val EXECUTE_TOOL_DISPATCH_EVENT: VarargEventId = GROUP.registerVarargEvent(
    "mcp.execute_tool.dispatch",
    DISPATCHED_TOOL_NAME,
    ARG_COUNT,
    DISPATCHED_TOOL_FOUND,
    SUCCESS,
    EventFields.DurationMs,
  )

  override fun getGroup(): EventLogGroup = GROUP

  fun logMcpToolCall(descriptor: McpToolDescriptor) = MCP_TOOL_CALL_EVENT.log(descriptor.name)

  fun logExecuteToolDispatch(dispatchedToolName: String, argCount: Int, found: Boolean, success: Boolean, durationMs: Long) {
    EXECUTE_TOOL_DISPATCH_EVENT.log(
      DISPATCHED_TOOL_NAME.with(dispatchedToolName),
      ARG_COUNT.with(argCount),
      DISPATCHED_TOOL_FOUND.with(found),
      SUCCESS.with(success),
      EventFields.DurationMs.with(durationMs),
    )
  }

  internal class McpToolNameValidator : CustomValidationRule() {
    override fun doValidate(data: String, context: EventContext): ValidationResultType {
      for ((ext, tools) in service<ScopeHolder>().valueMap.value) {
        if (tools.contains(data)) {
          return if (getPluginInfo(ext.javaClass).isSafeToReport()) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
        }
      }
      return ValidationResultType.REJECTED
    }

    override fun getRuleId(): String = "tool_name_validator_id"
  }
}

@Service(Service.Level.APP)
private class ScopeHolder(coroutineScope: CoroutineScope) {
  @JvmField
  val valueMap = resettableLazy {
    McpToolsProvider.EP.extensionList.associateWith { ext -> ext.getTools().asSequence().map { it.descriptor.name } }
  }

  init {
    McpToolsProvider.EP.addChangeListener(coroutineScope) { valueMap.reset() }
    McpToolset.EP.addChangeListener(coroutineScope) { valueMap.reset() }
  }
}