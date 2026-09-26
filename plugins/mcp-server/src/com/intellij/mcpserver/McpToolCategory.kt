package com.intellij.mcpserver

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.NameUtilCore
import org.jetbrains.annotations.Nls

/**
 * Represents a category for MCP tools.
 *
 * @property shortName Simple name of the class where the tool is declared (e.g., "PatchToolset")
 * @property fullyQualifiedName Fully qualified name of the class including package (e.g., "com.intellij.mcpserver.toolsets.general.PatchToolset")
 * @property isExperimental Whether this category contains experimental tools
 * @property alwaysIncluded Whether this category contains tools that should always be included as directly accessible MCP tools
 * @property displayName Optional plugin-provided human-readable name for this category (see [McpToolset.displayName]).
 * When `null`, [presentableName] derives a name from [shortName].
 * @property displayDescription Optional plugin-provided human-readable description for this category (see [McpToolset.displayDescription]).
 * When `null`, no group description is shown ([presentableDescription] is `null`).
 */
data class McpToolCategory(
  val shortName: @NlsSafe String,
  val fullyQualifiedName: @NlsSafe String,
  val isExperimental: Boolean = false,
  val alwaysIncluded: Boolean = false,
  val displayName: @Nls String? = null,
  val displayDescription: @Nls String? = null,
)

/**
 * The name to present in the UI: the plugin-provided [McpToolCategory.displayName] when available,
 * otherwise a name derived from the class' simple name (drop the `Toolset` suffix and split into words).
 */
val McpToolCategory.presentableName: @Nls String
  @Suppress("HardCodedStringLiteral")
  get() = displayName ?: NameUtilCore.splitNameIntoWordList(shortName.removeSuffix("Toolset")).joinToString(" ")

/**
 * The group description to present in the UI: the plugin-provided [McpToolCategory.displayDescription], or `null`
 * when the toolset did not provide one (groups have no agent-facing description to fall back to).
 */
val McpToolCategory.presentableDescription: @Nls String?
  get() = displayDescription
