package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.PatternUtil
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface McpToolFilterProvider {
  data class McpToolFilterContext(
    val disallowedTools: Set<McpTool>,
    val allowedTools: Set<McpTool>
  )

  enum class McpToolFilterAction {
    ALLOW,
    DISALLOW
  }

  interface McpToolFilterModification {
    fun apply(context: McpToolFilterContext): McpToolFilterContext
  }
  class AllowMcpTools(
    val toolList: Set<McpTool>,
  ) : McpToolFilterModification {
    override fun apply(context: McpToolFilterContext): McpToolFilterContext = context.copy(
      disallowedTools = context.disallowedTools - toolList,
      allowedTools = context.allowedTools + toolList
    )
  }
  class DisallowMcpTools(
    val toolList: Set<McpTool>,
  ) : McpToolFilterModification {
    override fun apply(context: McpToolFilterContext): McpToolFilterContext = context.copy(
      disallowedTools = context.disallowedTools + toolList,
      allowedTools = context.allowedTools - toolList
    )
  }

  interface McpToolFilter {
    fun modify(context: McpToolFilterContext): McpToolFilterModification
  }

  class MaskBasedMcpToolFilter(mask: String, val action: McpToolFilterAction) : McpToolFilter {
    private val matcher = PatternUtil.fromMask(mask)

    override fun modify(context: McpToolFilterContext): McpToolFilterModification {
      return if (action == McpToolFilterAction.ALLOW)
        AllowMcpTools(context.disallowedTools.filter { matcher.matcher(it.descriptor.fullyQualifiedName).matches() }.toSet())
      else
        DisallowMcpTools(context.allowedTools.filter { matcher.matcher(it.descriptor.fullyQualifiedName).matches() }.toSet())
    }

    companion object {
      fun getMaskFilters(maskList: String): List<McpToolFilter> =
        maskList
          .split(",")
          .map { it.trim() }
          .map {
            if (it.startsWith("-")) it.substring(1) to McpToolFilterAction.DISALLOW
            else if (it.startsWith("+")) it.substring(1) to McpToolFilterAction.ALLOW
            else it to McpToolFilterAction.ALLOW
          }
          .map { MaskBasedMcpToolFilter(it.first, it.second) }
    }
  }

  companion object {
    val EP: ExtensionPointName<McpToolFilterProvider> = ExtensionPointName.create<McpToolFilterProvider>("com.intellij.mcpServer.mcpToolFilterProvider")
  }

  fun getFilters(clientInfo: Implementation?, sessionOptions: McpServerService.McpSessionOptions? = null): List<McpToolFilter>

  fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions? = null): Flow<Unit>
}
