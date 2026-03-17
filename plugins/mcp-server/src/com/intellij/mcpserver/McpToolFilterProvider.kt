package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.extensions.ExtensionPointName
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface McpToolFilterProvider {
  enum class McpToolState {
    OFF,
    ON,
    ON_DEMAND
  }

  class McpToolFilterContext(
    onDemandTools: Collection<McpTool>
  ) {
    private val toolStates: MutableMap<McpTool, McpToolState> = onDemandTools.associateWith { McpToolState.ON_DEMAND }.toMutableMap()

    val onTools: Set<McpTool> get() = toolStates.filterValues { it == McpToolState.ON }.keys
    val onDemandTools: Set<McpTool> get() = toolStates.filterValues { it == McpToolState.ON_DEMAND }.keys

    /**
     * Turns on tools: transitions from ON_DEMAND to ON.
     * Cannot transition from OFF to ON.
     *
     * @param predicate lambda that returns true for tools that should be turned on
     */
    fun turnOn(predicate: (McpTool) -> Boolean) {
      for ((tool, state) in toolStates) {
        if (state == McpToolState.ON_DEMAND && predicate(tool)) {
          toolStates[tool] = McpToolState.ON
        }
      }
    }

    /**
     * Turns off tools: transitions from ON_DEMAND to OFF.
     *
     * @param predicate lambda that returns true for tools that should be turned off
     */
    fun turnOff(predicate: (McpTool) -> Boolean) {
      for ((tool, state) in toolStates) {
        if (state == McpToolState.ON_DEMAND && predicate(tool)) {
          toolStates[tool] = McpToolState.OFF
        }
      }
    }

    /**
     * Prohibits tools: transitions from both ON_DEMAND and ON to OFF.
     *
     * @param predicate lambda that returns true for tools that should be prohibited
     */
    fun prohibit(predicate: (McpTool) -> Boolean) {
      for ((tool, state) in toolStates) {
        if ((state == McpToolState.ON_DEMAND || state == McpToolState.ON) && predicate(tool)) {
          toolStates[tool] = McpToolState.OFF
        }
      }
    }
  }

  companion object {
    val EP: ExtensionPointName<McpToolFilterProvider> = ExtensionPointName.create<McpToolFilterProvider>("com.intellij.mcpServer.mcpToolFilterProvider")

    fun applyMaskFilter(context: McpToolFilterContext, maskList: String) {
      if (maskList.isBlank()) return
      val masks = MaskList(maskList)
      context.turnOff { tool -> !masks.matches(tool.descriptor.fullyQualifiedName) }
    }
  }

  fun applyFilters(context: McpToolFilterContext, clientInfo: Implementation?, sessionOptions: McpServerService.McpSessionOptions? = null)

  fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions? = null): Flow<Unit>
}
