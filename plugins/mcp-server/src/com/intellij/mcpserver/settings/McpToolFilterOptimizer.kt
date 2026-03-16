package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpToolCategory

/**
 * Utility object for building optimized filter strings for MCP tools.
 * 
 * Optimization rules:
 * 1. If all tools in a category are enabled/disabled, use category mask instead of individual tools
 * 2. If less than 50% of categories have at least 50% enabled tools, start with -* and then enable needed ones
 * 3. If less than 50% of tools in a category are enabled, disable the category and enable tools individually
 */
object McpToolFilterOptimizer {

  data class CategoryToolsInfo(
    val category: McpToolCategory,
    val toolFqns: Set<String>
  )

  /**
   * Build an optimized filter string based on enabled/disabled tools.
   * 
   * @param enabledTools set of fully qualified names of enabled tools
   * @param disabledTools set of fully qualified names of disabled tools
   * @param categoriesInfo list of category information with their tools
   * @return optimized filter string
   */
  fun buildOptimizedFilter(
    enabledTools: Set<String>,
    disabledTools: Set<String>,
    categoriesInfo: List<CategoryToolsInfo>
  ): String {
    if (disabledTools.isEmpty()) {
      return "*" // All enabled
    }
    if (enabledTools.isEmpty()) {
      return "-*" // All disabled
    }

    // Analyze categories
    data class CategoryStats(
      val category: McpToolCategory,
      val totalTools: Int,
      val enabledCount: Int,
      val enabledTools: Set<String>,
      val disabledTools: Set<String>
    ) {
      val enabledPercent: Double get() = if (totalTools > 0) enabledCount.toDouble() / totalTools else 0.0
      val allEnabled: Boolean get() = enabledCount == totalTools
      val allDisabled: Boolean get() = enabledCount == 0
    }

    val categoryStats = categoriesInfo.map { info ->
      val enabled = info.toolFqns.intersect(enabledTools)
      val disabled = info.toolFqns.intersect(disabledTools)
      CategoryStats(info.category, info.toolFqns.size, enabled.size, enabled, disabled)
    }

    // Count categories with at least 50% enabled tools
    val categoriesWithMajorityEnabled = categoryStats.count { it.enabledPercent >= 0.5 }
    val totalCategories = categoryStats.size

    // Decide starting mode: if less than 50% of categories have majority enabled, start with -* (deny all)
    val startWithDenyAll = categoriesWithMajorityEnabled < totalCategories * 0.5

    val filterParts = mutableListOf<String>()

    if (startWithDenyAll) {
      // Start with deny all, then enable categories/tools
      filterParts.add("-*")

      for (stats in categoryStats) {
        if (stats.allDisabled) {
          // Skip - already disabled by -*
          continue
        }

        if (stats.allEnabled) {
          // Enable entire category
          filterParts.add("+${stats.category.fullyQualifiedName}.*")
        }
        else if (stats.enabledPercent >= 0.5) {
          // Enable category, then disable individual tools
          filterParts.add("+${stats.category.fullyQualifiedName}.*")
          for (toolFqn in stats.disabledTools.sorted()) {
            filterParts.add("-$toolFqn")
          }
        }
        else {
          // Less than 50% enabled - enable tools individually
          for (toolFqn in stats.enabledTools.sorted()) {
            filterParts.add("+$toolFqn")
          }
        }
      }
    }
    else {
      // Start with allow all (implicit), then disable categories/tools
      // We don't need to add "*" at start since it's the default

      for (stats in categoryStats) {
        if (stats.allEnabled) {
          // Skip - already enabled by default
          continue
        }

        if (stats.allDisabled) {
          // Disable entire category
          filterParts.add("-${stats.category.fullyQualifiedName}.*")
        }
        else if (stats.enabledPercent < 0.5) {
          // Less than 50% enabled - disable category, then enable individual tools
          filterParts.add("-${stats.category.fullyQualifiedName}.*")
          for (toolFqn in stats.enabledTools.sorted()) {
            filterParts.add("+$toolFqn")
          }
        }
        else {
          // More than 50% enabled - disable individual tools
          for (toolFqn in stats.disabledTools.sorted()) {
            filterParts.add("-$toolFqn")
          }
        }
      }
    }

    return if (filterParts.isEmpty()) McpToolFilterSettings.DEFAULT_FILTER else filterParts.joinToString(",")
  }
}
