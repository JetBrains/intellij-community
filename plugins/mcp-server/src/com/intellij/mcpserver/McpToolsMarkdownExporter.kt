// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Utility class for exporting MCP tools information to Markdown format.
 */
object McpToolsMarkdownExporter {
  
  /**
   * Generates markdown documentation for the provided tools grouped by category.
   */
  fun generateMarkdown(toolsByCategory: Map<McpToolCategory, List<McpTool>>): String {
    return buildString {
      appendLine("# MCP Tools")
      appendLine()
      for ((category, categoryTools) in toolsByCategory) {
        appendLine("## ${category.shortName}")
        appendLine()
        for (tool in categoryTools) {
          appendToolSection(tool, headingLevel = 3)
        }
      }
    }
  }

  /**
   * Generates a single-page markdown document for one MCP tool: top-level heading, full description,
   * input parameters table, and output schema table (when present).
   *
   * Use together with [generateMarkdownTree] when emitting per-tool reference files.
   */
  fun generateMarkdownForTool(tool: McpTool): String = buildString {
    appendToolSection(tool, headingLevel = 1)
  }

  /**
   * Generates a tree of markdown files for the provided tools:
   * - `tools.md` — index grouped by category, links to per-tool files;
   * - `tools/<tool_name>.md` — full reference per tool (description + tables).
   *
   * Keys are forward-slash relative paths. The caller writes each entry to disk.
   */
  fun generateMarkdownTree(tools: List<McpTool>): Map<String, String> {
    val byCategory = tools
      .groupBy { it.descriptor.category }
      .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shortName })
      .mapValues { (_, categoryTools) -> categoryTools.sortedBy { it.descriptor.name.lowercase() } }

    val result = LinkedHashMap<String, String>()
    result[TREE_INDEX_FILE] = buildString {
      appendLine("# MCP Tools")
      appendLine()
      for ((category, categoryTools) in byCategory) {
        appendLine("## ${category.shortName}")
        appendLine()
        for (tool in categoryTools) {
          val firstLine = tool.descriptor.description.trim().lineSequence()
            .firstOrNull { it.isNotBlank() }.orEmpty().escapeMarkdown()
          val name = tool.descriptor.name
          appendLine("- [$name]($TREE_TOOLS_SUBDIR/$name.md) — $firstLine")
        }
        appendLine()
      }
    }
    for ((_, categoryTools) in byCategory) {
      for (tool in categoryTools) {
        result["$TREE_TOOLS_SUBDIR/${tool.descriptor.name}.md"] = generateMarkdownForTool(tool)
      }
    }
    return result
  }

  /**
   * Index file name in [generateMarkdownTree] output.
   */
  const val TREE_INDEX_FILE: String = "tools.md"

  /**
   * Subdirectory holding per-tool markdown files in [generateMarkdownTree] output.
   */
  const val TREE_TOOLS_SUBDIR: String = "tools"

  private fun StringBuilder.appendToolSection(tool: McpTool, headingLevel: Int) {
    val heading = "#".repeat(headingLevel)
    appendLine("$heading ${tool.descriptor.name}")
    appendLine(tool.descriptor.description.trimIndent().escapeLineBreaks().escapeMarkdown())
    appendLine()
    appendLine("$heading# Parameters")
    val inputRows = tool.descriptor.inputSchema.toSchemaTableRows()
    if (inputRows.isEmpty()) {
      appendLine("No parameters.")
    }
    else {
      appendLine("| Name | Type | Description |")
      appendLine("| --- | --- | --- |")
      for (row in inputRows) {
        appendLine("| ${row.name} | ${row.type.escapeMarkdown()} | ${row.description?.trimIndent()?.escapeLineBreaks()?.escapeMarkdown() ?: ""} |")
      }
    }
    appendLine()
    val outputSchema = tool.descriptor.outputSchema
    if (outputSchema != null) {
      appendLine("$heading# Output")
      val outputRows = outputSchema.toSchemaTableRows()
      if (outputRows.isEmpty()) {
        appendLine("No output fields.")
      }
      else {
        appendLine("| Name | Type | Description |")
        appendLine("| --- | --- | --- |")
        for (row in outputRows) {
          appendLine("| ${row.name} | ${row.type.escapeMarkdown()} | ${row.description?.trimIndent()?.escapeLineBreaks()?.escapeMarkdown() ?: ""} |")
        }
      }
      appendLine()
    }
  }

  /**
   * Generates markdown documentation for the provided tools, grouping by category internally.
   */
  fun generateMarkdown(tools: List<McpTool>): String {
    val toolsByCategory = tools
      .groupBy { it.descriptor.category }
      .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shortName })
      .mapValues { (_, categoryTools) -> categoryTools.sortedBy { it.descriptor.name.lowercase() } }
    return generateMarkdown(toolsByCategory)
  }

  /**
   * Generates markdown documentation for all currently available MCP tools.
   */
  fun generateMarkdownForAllTools(): String {
    return generateMarkdown(McpServerService.getInstance().getMcpTools())
  }

  private data class SchemaTableRow(
    val name: String,
    val type: String,
    val description: String?
  )

  /**
   * Converts the schema to a list of table rows with recursive support for nested objects.
   * Nested properties are indented based on their depth level.
   */
  private fun McpToolSchema.toSchemaTableRows(): List<SchemaTableRow> {
    val rows = mutableListOf<SchemaTableRow>()
    collectPropertiesRecursively(propertiesSchema, requiredProperties, rows, depth = 0, prefix = "")
    return rows
  }

  private fun collectPropertiesRecursively(
    properties: JsonObject,
    requiredProps: Set<String>,
    rows: MutableList<SchemaTableRow>,
    depth: Int,
    prefix: String
  ) {
    val indent = "&nbsp;&nbsp;".repeat(depth)
    
    for ((name, schema) in properties) {
      val schemaObject = schema as? JsonObject ?: continue
      val isRequired = name in requiredProps
      val displayName = "$indent$prefix${if (isRequired) "$name*" else name}"
      
      val description = schemaObject["description"]?.let { (it as? JsonPrimitive)?.content }
      val type = extractType(schemaObject)
      
      rows.add(SchemaTableRow(displayName, type, description))
      
      // Handle nested object properties
      val nestedProperties = schemaObject["properties"] as? JsonObject
      if (nestedProperties != null) {
        val nestedRequired = extractRequiredSet(schemaObject)
        collectPropertiesRecursively(nestedProperties, nestedRequired, rows, depth + 1, "")
      }
      
      // Handle array items with nested properties
      val items = schemaObject["items"] as? JsonObject
      if (items != null) {
        val itemProperties = items["properties"] as? JsonObject
        if (itemProperties != null) {
          val itemRequired = extractRequiredSet(items)
          collectPropertiesRecursively(itemProperties, itemRequired, rows, depth + 1, "[].")
        }
      }
    }
  }

  private fun extractType(schemaObject: JsonObject): String {
    val typeElement = schemaObject["type"]
    val enumValues = schemaObject["enum"] as? JsonArray
    
    // Handle type as array (e.g., ["boolean", "null"] -> "boolean?")
    val rawType = when (typeElement) {
      is JsonPrimitive -> typeElement.content
      is JsonArray -> {
        val types = typeElement.mapNotNull { (it as? JsonPrimitive)?.content }
        val nonNullTypes = types.filter { it != "null" }
        val isNullable = "null" in types
        val baseType = nonNullTypes.firstOrNull() ?: "any"
        return if (isNullable) "$baseType?" else baseType
      }
      else -> null
    }
    
    return when {
      enumValues != null && (rawType == null || rawType == "string") -> {
        enumValues.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(" \\| ")
      }
      rawType == "array" -> {
        val items = schemaObject["items"] as? JsonObject
        val itemType = items?.let { extractType(it) } ?: "any"
        "array[$itemType]"
      }
      rawType != null -> rawType
      else -> "N/A"
    }
  }

  private fun extractRequiredSet(schemaObject: JsonObject): Set<String> {
    val required = schemaObject["required"] as? JsonArray ?: return emptySet()
    return required.mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
  }

  private fun String.escapeLineBreaks(): String = 
    replace("\r\n", "<br/>").replace("\n", "<br/>").replace("\r", "<br/>")

  private fun String.escapeMarkdown(): String = replace("|", "\\|")
}
