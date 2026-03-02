package com.intellij.mcpserver

import com.intellij.util.PatternUtil

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
   * @param toolName the name of the tool to check
   * @return true if the tool should be included, false otherwise
   */
  fun shouldInclude(toolName: String): Boolean

  /**
   * Allow-all filter: all tools are included.
   *
   * This is the default filter when no restrictions are needed.
   * Use this instead of null to explicitly indicate no filtering.
   */
  data object AllowAll : McpToolFilter {
    override fun shouldInclude(toolName: String): Boolean = true
  }

  /**
   * Allow-list filter: only tools with names in the allowed set are included.
   *
   * This is useful for exposing a specific subset of tools to an agent,
   * for example, limiting an agent to only file operations.
   *
   * @property allowedTools set of tool names that should be included
   */
  data class AllowList(val allowedTools: Set<String>) : McpToolFilter {
    override fun shouldInclude(toolName: String): Boolean = toolName in allowedTools
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
   * @property maskList comma-separated list of mask patterns with +/- prefixes
   */
  class MaskBased(private val maskList: String) : McpToolFilter {
    private enum class Action { ALLOW, DISALLOW }

    private data class MaskEntry(val pattern: java.util.regex.Pattern, val action: Action)

    private val masks: List<MaskEntry> = maskList
      .split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { mask ->
        val (pattern, action) = when {
          mask.startsWith("-") -> mask.substring(1) to Action.DISALLOW
          mask.startsWith("+") -> mask.substring(1) to Action.ALLOW
          else -> mask to Action.ALLOW
        }
        MaskEntry(PatternUtil.fromMask(pattern), action)
      }

    override fun shouldInclude(toolName: String): Boolean {
      // Default is to include if no masks match
      var result = true
      for (entry in masks) {
        if (entry.pattern.matcher(toolName).matches()) {
          result = entry.action == Action.ALLOW
        }
      }
      return result
    }

    companion object {
      /**
       * Creates a MaskBased filter from a mask list string.
       *
       * @param maskList comma-separated list of mask patterns with +/- prefixes
       * @return a new MaskBased filter, or AllowAll if the mask list is empty
       */
      fun fromMaskList(maskList: String?): McpToolFilter {
        return if (maskList == null || maskList.isBlank()) AllowAll else MaskBased(maskList)
      }
    }
  }
}
