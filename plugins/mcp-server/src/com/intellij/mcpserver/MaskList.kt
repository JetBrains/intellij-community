package com.intellij.mcpserver

import com.intellij.util.PatternUtil
import java.util.regex.Pattern

/**
 * Represents a list of mask patterns for filtering tool names.
 * 
 * Each mask can be prefixed with '+' (allow) or '-' (disallow).
 * Masks without a prefix are treated as allow patterns.
 * Masks are applied in order, and the last matching mask determines the result.
 */
class MaskList(maskList: String) {
  enum class Action { ALLOW, DISALLOW }

  data class MaskEntry(val pattern: Pattern, val action: Action) {
    fun matches(toolName: String): Boolean = pattern.matcher(toolName).matches()
  }

  private val masks: List<MaskEntry> = parseMasks(maskList)

  /**
   * Checks if a tool name matches the mask list.
   * The last matching mask determines the result.
   * Returns true if the tool should be allowed.
   */
  fun matches(toolName: String): Boolean {
    var isAllowed = true
    for (entry in masks) {
      if (entry.matches(toolName)) {
        isAllowed = entry.action == Action.ALLOW
      }
    }
    return isAllowed
  }

  private fun parseMasks(maskList: String): List<MaskEntry> =
    maskList
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
}
