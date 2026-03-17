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
    val toolStates: Map<McpTool, McpToolState> = onDemandTools.associateWith { McpToolState.ON_DEMAND }
    
    private constructor(toolStates: Map<McpTool, McpToolState>) : this(emptyList()) {
      (this.toolStates as MutableMap).putAll(toolStates)
    }
    
    val onTools: Set<McpTool> get() = toolStates.filterValues { it == McpToolState.ON }.keys
    val onDemandTools: Set<McpTool> get() = toolStates.filterValues { it == McpToolState.ON_DEMAND }.keys
    
    fun copy(toolStates: Map<McpTool, McpToolState> = this.toolStates): McpToolFilterContext {
      return McpToolFilterContext(toolStates)
    }
  }

  interface McpToolFilterModification {
    fun apply(context: McpToolFilterContext): McpToolFilterContext
  }

  /**
   * Turns on tools: transitions from ON_DEMAND to ON.
   * Cannot transition from OFF to ON.
   * 
   * @param predicate lambda that returns true for tools that should be turned on
   */
  class TurnOnMcpTools(
    val predicate: (McpTool) -> Boolean,
  ) : McpToolFilterModification {
    override fun apply(context: McpToolFilterContext): McpToolFilterContext {
      val newStates = context.toolStates.toMutableMap()
      for ((tool, state) in context.toolStates) {
        if (state == McpToolState.ON_DEMAND && predicate(tool)) {
          newStates[tool] = McpToolState.ON
        }
        // Ignore if OFF or already ON
      }
      return context.copy(toolStates = newStates)
    }
  }

  /**
   * Turns off tools: transitions from ON_DEMAND to OFF.
   *
   * @param predicate lambda that returns true for tools that should be turned off
   */
  class TurnOffMcpTools(
    val predicate: (McpTool) -> Boolean,
  ) : McpToolFilterModification {
    override fun apply(context: McpToolFilterContext): McpToolFilterContext {
      val newStates = context.toolStates.toMutableMap()
      for ((tool, state) in context.toolStates) {
        if (state == McpToolState.ON_DEMAND && predicate(tool)) {
          newStates[tool] = McpToolState.OFF
        }
        // Ignore if already OFF
      }
      return context.copy(toolStates = newStates)
    }
  }

  interface McpToolFilter {
    fun modify(context: McpToolFilterContext): McpToolFilterModification
  }

  class MaskBasedMcpToolFilter(val maskList: MaskList) : McpToolFilter {

    override fun modify(context: McpToolFilterContext): McpToolFilterModification {
      return TurnOffMcpTools { tool ->
        !maskList.matches(tool.descriptor.fullyQualifiedName)
      }
    }

    companion object {
      fun getMaskFilters(maskList: String): List<McpToolFilter> {
        val masks = MaskList(maskList)
        return if (maskList.isBlank()) emptyList() else listOf(MaskBasedMcpToolFilter(masks))
      }
    }
  }

  companion object {
    val EP: ExtensionPointName<McpToolFilterProvider> = ExtensionPointName.create<McpToolFilterProvider>("com.intellij.mcpServer.mcpToolFilterProvider")
  }

  fun getFilters(clientInfo: Implementation?, sessionOptions: McpServerService.McpSessionOptions? = null): List<McpToolFilter>

  fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions? = null): Flow<Unit>
}
