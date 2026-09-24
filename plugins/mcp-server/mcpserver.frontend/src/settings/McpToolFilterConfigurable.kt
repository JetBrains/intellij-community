// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.frontend.settings

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCategory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComponent

internal const val EXPAND_LINK: String = "..."
internal const val DESCRIPTION_COLLAPSED_LINK_GAP: Int = 4
private const val DESCRIPTION_COLLAPSED_MAX_LENGTH = 140

data class ToolCategoryGroup(
  val category: McpToolCategory,
  val tools: List<McpTool>,
)

data class DescriptionRenderModel(
  val collapsedPreview: String,
  val collapsedTruncated: Boolean,
  val expandedRows: List<@NlsSafe String>,
)

fun buildCategoryGroups(tools: List<McpTool>): List<ToolCategoryGroup> {
  return tools
    .groupBy { it.descriptor.category }
    .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shortName })
    .map { (category, categoryTools) ->
      ToolCategoryGroup(
        category = category,
        tools = categoryTools.sortedBy { it.descriptor.name.lowercase() },
      )
    }
}

fun userConfigurableTools(tools: List<McpTool>): List<McpTool> = tools.filter { it.isUserConfigurable }

fun buildDescriptionRenderModel(
  description: String,
  collapsedTextWidth: Int,
  expandedTextWidth: Int,
  textWidth: (String) -> Int,
): DescriptionRenderModel {
  val expandedDescription = description.trimIndent().trim()
  val collapsedDescription = expandedDescription
    .lineSequence()
    .joinToString(" ") { it.trim() }
    .replace(Regex("\\s+"), " ")
    .trim()
  val collapsedCandidate = collapsedDescription.take(DESCRIPTION_COLLAPSED_MAX_LENGTH).trimEnd()
  val fullWidthPreview = truncateTextToWidth(collapsedCandidate, collapsedTextWidth, textWidth)
  val needsTruncation = fullWidthPreview.length < collapsedDescription.length
  val collapsedPreview = if (needsTruncation) {
    truncateTextToWidth(
      collapsedCandidate,
      (collapsedTextWidth - textWidth(EXPAND_LINK) - DESCRIPTION_COLLAPSED_LINK_GAP).coerceAtLeast(0),
      textWidth,
    )
  }
  else {
    fullWidthPreview
  }

  val expandedRows = if (expandedDescription.isEmpty()) {
    emptyList()
  }
  else {
    wrapTextIntoRows(expandedDescription, expandedTextWidth, textWidth)
  }

  return DescriptionRenderModel(
    collapsedPreview = collapsedPreview,
    collapsedTruncated = needsTruncation,
    expandedRows = expandedRows,
  )
}

internal fun truncateTextToWidth(
  text: String,
  maxWidth: Int,
  textWidth: (String) -> Int,
): String {
  if (text.isEmpty() || textWidth(text) <= maxWidth) return text

  var low = 0
  var high = text.length
  while (low < high) {
    val mid = (low + high + 1) / 2
    if (textWidth(text.substring(0, mid)) <= maxWidth) {
      low = mid
    }
    else {
      high = mid - 1
    }
  }
  return text.substring(0, low).trimEnd()
}

//This works better compared to using raw text in JBTextArea
private fun wrapTextIntoRows(text: String, availableWidth: Int, textWidth: (String) -> Int): List<String> {
  if (text.isBlank()) return listOf("")
  if (textWidth(text) <= availableWidth) return listOf(text)

  val rows = mutableListOf<String>()
  val words = text.split(Regex("\\s+"))
  var currentRow = ""
  for (word in words) {
    val candidate = if (currentRow.isEmpty()) word else "$currentRow $word"
    when {
      textWidth(candidate) <= availableWidth -> currentRow = candidate
      currentRow.isNotEmpty() && textWidth(word) <= availableWidth -> {
        rows += currentRow
        currentRow = word
      }
      else -> {
        if (currentRow.isNotEmpty()) rows += currentRow
        rows += splitLongWord(word, availableWidth, textWidth)
        currentRow = ""
      }
    }
  }
  if (currentRow.isNotEmpty()) {
    rows += currentRow
  }
  return rows
}

private fun splitLongWord(word: String, availableWidth: Int, textWidth: (String) -> Int): List<String> {
  if (word.isEmpty()) return emptyList()

  val result = mutableListOf<String>()
  var remaining = word
  while (remaining.isNotEmpty()) {
    val chunk = truncateTextToWidth(remaining, availableWidth, textWidth)
      .ifEmpty { remaining.first().toString() }
    result += chunk
    remaining = remaining.drop(chunk.length)
  }
  return result
}

/**
 * Configurable for managing MCP tool exposure in a grouped-list UI.
 *
 * The instance itself is created on a background thread, so it must not touch Swing. All the UI lives in
 * [McpToolFilterPanel], which is created lazily from [createComponent] on the EDT (IJPL-256380).
 */
@ApiStatus.Internal
class McpToolFilterConfigurable : SearchableConfigurable {
  @get:VisibleForTesting
  @get:ApiStatus.Internal
  var panel: McpToolFilterPanel? = null
    private set

  override fun getDisplayName(): String = McpServerBundle.message("configurable.mcp.tool.filter")

  override fun getId(): @NonNls String = "com.intellij.mcpserver.settings.filter"

  override fun createComponent(): JComponent {
    val panel = this.panel ?: McpToolFilterPanel().also { this.panel = it }
    return panel.component
  }

  override fun isModified(): Boolean = panel?.isModified() == true

  override fun apply() {
    panel?.apply()
  }

  override fun reset() {
    panel?.reset()
  }

  override fun disposeUIResources() {
    panel?.dispose()
    panel = null
  }

  /**
   * Builds the tool list off the EDT and populates the UI with it.
   *
   * Visible for tests: the UI loads the tools on show, which never happens for a component that is not displayed.
   */
  @ApiStatus.Internal
  @VisibleForTesting
  suspend fun loadToolsAndUpdateUi() {
    panel?.loadToolsAndUpdateUi()
  }
}
