package com.intellij.mcpserver

/**
 * Filter for selecting which MCP tools should be exposed in a server session.
 *
 * Different filter implementations allow for flexible tool selection strategies.
 * Filters are applied when registering tools for an MCP server session, allowing
 * different agents to see different subsets of available tools.
 */
sealed interface McpToolFilter {
  /**
   * Checks if a tool with the given name should be included in the session.
   *
   * @param tool the tool to check
   * @return true if the tool should be included, false otherwise
   */
  fun shouldInclude(tool: McpTool): Boolean

  data object AllowNonExperimental : McpToolFilter {
    override fun shouldInclude(tool: McpTool): Boolean = !tool.descriptor.category.isExperimental
  }

  interface TextMcpToolFilter : McpToolFilter {
    override fun shouldInclude(tool: McpTool): Boolean = shouldInclude(tool.descriptor.fullyQualifiedName)
    fun shouldInclude(toolName: String): Boolean
  }
  /**
   * Allow-all filter: all tools are included.
   *
   * This is the default filter when no restrictions are needed.
   * Use this instead of null to explicitly indicate no filtering.
   */
  data object AllowAll : TextMcpToolFilter {
    override fun shouldInclude(toolName: String): Boolean = true
  }

  data object ProhibitAll : TextMcpToolFilter {
    override fun shouldInclude(toolName: String): Boolean = false
  }

  /**
   * Allow-list filter: only tools with names in the allowed set are included.
   *
   * This is useful for exposing a specific subset of tools to an agent,
   * for example, limiting an agent to only file operations.
   *
   * @property allowedTools set of tool names that should be included
   */
  data class AllowList(val allowedTools: Set<String>) : TextMcpToolFilter {
    override fun shouldInclude(toolName: String): Boolean = toolName.split('.').last() in allowedTools
  }

  /**
   * Mask-based filter: filters tools based on a comma-separated list of mask patterns.
   *
   * Each mask can be prefixed with '+' (allow) or '-' (disallow).
   * Masks without a prefix are treated as allow patterns.
   * Masks are applied in order, and the last matching mask determines the result.
   *
   * Example: `-*,+com.intellij.mcpserver.toolsets.general.*,-*.get_file_text_by_path`
   * - First disallows all tools
   * - Then allows all tools under `com.intellij.mcpserver.toolsets.general`
   * - Then disallows the specific tool `get_file_text_by_path` from any package
   *
   * @param maskList comma-separated list of mask patterns with +/- prefixes
   */
  class MaskBased(maskList: String) : TextMcpToolFilter {
    private val maskList: MaskList = MaskList(maskList)

    override fun shouldInclude(toolName: String): Boolean {
      return maskList.matches(toolName)
    }

    companion object {
      /**
       * Creates a MaskBased filter from a mask list string.
       *
       * @param maskList comma-separated list of mask patterns with +/- prefixes
       * @return a new MaskBased filter that denies all if maskList is empty, or AllowAll if null/blank
       */
      fun fromMaskList(maskList: String?): McpToolFilter {
        if (maskList.isNullOrBlank()) {
          return ProhibitAll // Empty mask = deny all
        }
        return MaskBased(maskList)
      }
    }
  }
}
