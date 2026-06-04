// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.mcpserver.McpToolsMarkdownExporter.TREE_INDEX_FILE
import com.intellij.mcpserver.McpToolsMarkdownExporter.TREE_TOOLS_SUBDIR
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class McpToolsMarkdownExporterTest {

  @Test
  fun singleToolWithSimpleInputAndNoOutputRendersParametersTable() {
    val expected = md(
      "# run_to_target",
      "Resumes execution to the specified line.",
      "",
      "## Parameters",
      "| Name | Type | Description |",
      "| --- | --- | --- |",
      "| sessionId* | string | Active session identifier |",
      "| line* | integer | 1-based line number |",
      "",
    )

    assertEquals(expected, McpToolsMarkdownExporter.generateMarkdownForTool(runToTargetTool()))
  }

  @Test
  fun toolWithOutputSchemaRendersBothParametersAndOutputSections() {
    val expected = md(
      "# mutate_value",
      "Updates the value at the supplied path.",
      "",
      "## Parameters",
      "| Name | Type | Description |",
      "| --- | --- | --- |",
      "| path* | string | Target path |",
      "| newValue* | string | Replacement value |",
      "",
      "## Output",
      "| Name | Type | Description |",
      "| --- | --- | --- |",
      "| previousValue | string | Value prior to update |",
      "",
    )

    assertEquals(expected, McpToolsMarkdownExporter.generateMarkdownForTool(mutateValueTool()))
  }

  @Test
  fun emptyInputSchemaRendersNoParametersLine() {
    val expected = md(
      "# ping",
      "Performs no work.",
      "",
      "## Parameters",
      "No parameters.",
      "",
    )

    assertEquals(expected, McpToolsMarkdownExporter.generateMarkdownForTool(pingTool()))
  }

  @Test
  fun emptyOutputSchemaRendersNoOutputFieldsLine() {
    val expected = md(
      "# ping",
      "Performs no work.",
      "",
      "## Parameters",
      "No parameters.",
      "",
      "## Output",
      "No output fields.",
      "",
    )

    assertEquals(expected, McpToolsMarkdownExporter.generateMarkdownForTool(pingWithEmptyOutputTool()))
  }

  @Test
  fun pipeCharacterEscapedInDescriptionAndTableTypeAndDescriptionCells() {
    val t = tool(
      name = "alt|tool",
      description = "Provide configurationName | filePath+line.",
      input = schema(required = setOf("input")) {
        putJsonObject("input") {
          put("type", "string")
          put("description", "Accepts a | separated list.")
        }
      },
    )

    val output = McpToolsMarkdownExporter.generateMarkdownForTool(t)

    // Tool-name heading is rendered verbatim (no escaping for headings).
    assert(output.contains("# alt|tool\n")) { "Heading should be raw: $output" }
    // Description line is escaped because it shares the markdown surface with the tables.
    assert(output.contains("Provide configurationName \\| filePath+line.\n")) {
      "Description pipes should be escaped: $output"
    }
    // Table cells: description column is escaped.
    assert(output.contains("| input* | string | Accepts a \\| separated list. |\n")) {
      "Table row description pipes should be escaped: $output"
    }
  }

  @Test
  fun lfLineBreakEscapedToBrTagInDescription() {
    assertLineBreakEscaped("Starts a task.\nReturns the handle.")
  }

  @Test
  fun crlfLineBreakEscapedToBrTagInDescription() {
    assertLineBreakEscaped("Starts a task.\r\nReturns the handle.")
  }

  @Test
  fun crLineBreakEscapedToBrTagInDescription() {
    assertLineBreakEscaped("Starts a task.\rReturns the handle.")
  }

  @Test
  fun requiredPropertyGetsAsteriskSuffixAndOptionalDoesNot() {
    val output = McpToolsMarkdownExporter.generateMarkdownForTool(inspectNodeTool())
    assert(output.contains("| sessionId* | string |")) {
      "Required name should have asterisk: $output"
    }
    assert(output.contains("| path* | string |")) {
      "Required name should have asterisk: $output"
    }
    assert(output.contains("| frameIndex | integer |")) {
      "Optional name should not have asterisk: $output"
    }
  }

  @Test
  fun nullableTypeUnionRendersWithTrailingQuestionMark() {
    val output = McpToolsMarkdownExporter.generateMarkdownForTool(setMarkerTool())
    assert(output.contains("| silenced | boolean? | Suppresses notifications when true |")) {
      "Nullable union should render as `boolean?`: $output"
    }
  }

  @Test
  fun enumRendersAsPipeSeparatedList() {
    val output = McpToolsMarkdownExporter.generateMarkdownForTool(setMarkerTool())
    // `extractType` pre-escapes pipes (separator `" \\| "`) and then `appendToolSection`
    // applies `escapeMarkdown` again — so each `|` between values ends up with two
    // preceding backslashes. The test locks this current behavior.
    assert(output.contains("| priority | LOW \\\\| MEDIUM \\\\| HIGH | Priority level |")) {
      "Enum should render with escaped pipe separators: $output"
    }
  }

  @Test
  fun nestedObjectPropertiesIndentedWithNbspPerDepthLevel() {
    val output = McpToolsMarkdownExporter.generateMarkdownForTool(getStateTool())
    assert(output.contains("| location | object | Current cursor position |")) {
      "Outer object row missing: $output"
    }
    assert(output.contains("| &nbsp;&nbsp;path | string | File path |")) {
      "Nested property should be indented with one level of nbsp: $output"
    }
    assert(output.contains("| &nbsp;&nbsp;line | integer | 1-based line |")) {
      "Nested integer property missing: $output"
    }
    assert(output.contains("| &nbsp;&nbsp;column | integer? | 1-based column when known |")) {
      "Nested nullable property missing: $output"
    }
  }

  @Test
  fun arrayOfObjectsNestsItemsWithBracketPrefixAndNbspIndent() {
    val output = McpToolsMarkdownExporter.generateMarkdownForTool(getStateTool())
    assert(output.contains("| entries | array[object] | Active task entries |")) {
      "Array container row missing: $output"
    }
    assert(output.contains("| &nbsp;&nbsp;[].id | string | Identifier |")) {
      "Array item property should have `[].` prefix and one-level indent: $output"
    }
    assert(output.contains("| &nbsp;&nbsp;[].name | string | Display name |")) {
      "Array item name property missing: $output"
    }
  }

  @Test
  fun arrayOfPrimitiveRendersArrayBracketType() {
    val output = McpToolsMarkdownExporter.generateMarkdownForTool(filterByTagsTool())
    assert(output.contains("| tags | array[string] | Tag filter |")) {
      "Array of primitive should render as `array[string]`: $output"
    }
  }

  @Test
  fun missingTypeFallsBackToNA() {
    val output = McpToolsMarkdownExporter.generateMarkdownForTool(typelessParamTool())
    assert(output.contains("| payload | N/A | Untyped payload — exporter renders type as N/A |")) {
      "Property without type should render as N/A: $output"
    }
  }

  @Test
  fun generateMarkdownByCategoryUsesHeadingLevel3ForTools() {
    val expected = md(
      "# Tools",
      "",
      "## GeneralToolset",
      "",
      "### ping",
      "Performs no work.",
      "",
      "#### Parameters",
      "No parameters.",
      "",
    )

    val actual = McpToolsMarkdownExporter.generateMarkdown(mapOf(DEFAULT_CATEGORY to listOf(pingTool())))
    assertEquals(expected, actual)
  }

  @Test
  fun generateMarkdownListGroupsAndSortsCaseInsensitively() {
    val general = DEFAULT_CATEGORY
    val files = McpToolCategory(shortName = "FileToolset", fullyQualifiedName = "com.example.FileToolset")

    val tools = listOf(
      tool(name = "writer", category = files, description = "f-w"),
      tool(name = "Reader", category = files, description = "f-r"),
      tool(name = "ping", category = general, description = "g-p"),
      tool(name = "Marker", category = general, description = "g-m"),
    )

    val output = McpToolsMarkdownExporter.generateMarkdown(tools)

    // FileToolset comes before GeneralToolset (case-insensitive alphabetical).
    val fileIdx = output.indexOf("## FileToolset")
    val generalIdx = output.indexOf("## GeneralToolset")
    // Within FileToolset: Reader, writer (case-insensitive name sort: "reader" < "writer").
    val readerIdx = output.indexOf("### Reader")
    val writerIdx = output.indexOf("### writer")
    // Within GeneralToolset: Marker, ping ("marker" < "ping").
    val markerIdx = output.indexOf("### Marker")
    val pingIdx = output.indexOf("### ping")

    assert(fileIdx in 0..<generalIdx) { "FileToolset should precede GeneralToolset: $output" }
    assert(readerIdx in fileIdx..<writerIdx) { "Reader should precede writer in FileToolset: $output" }
    assert(markerIdx in generalIdx..<pingIdx) { "Marker should precede ping in GeneralToolset: $output" }
  }

  @Test
  fun generateMarkdownEmptyMapRendersOnlyTopHeading() {
    val expected = md(
      "# Tools",
      "",
    )

    assertEquals(expected, McpToolsMarkdownExporter.generateMarkdown(emptyMap()))
  }

  @Test
  fun generateMarkdownTreeReturnsIndexPlusPerToolFileForEachTool() {
    val result = McpToolsMarkdownExporter.generateMarkdownTree(
      listOf(getStateTool(), setMarkerTool()),
    )

    assertEquals(
      setOf(
        TREE_INDEX_FILE,
        "$TREE_TOOLS_SUBDIR/get_state.md",
        "$TREE_TOOLS_SUBDIR/set_marker.md",
      ),
      result.keys,
    )
  }

  @Test
  fun generateMarkdownTreeIndexContainsLinksAndFirstNonBlankDescriptionLine() {
    val t = startTaskTool(
      description = "\n\nStarts a task using the supplied configuration.\nReturns the task handle.",
    )

    val result = McpToolsMarkdownExporter.generateMarkdownTree(listOf(t))
    val index = result.getValue(TREE_INDEX_FILE)

    assert(index.contains("- [start_task]($TREE_TOOLS_SUBDIR/start_task.md) — Starts a task using the supplied configuration.\n")) {
      "Index should link to the per-tool file and show first non-blank description line: $index"
    }
  }

  @Test
  fun generateMarkdownTreePerToolFileMatchesFirstLineOmittedSingleToolOutput() {
    val t = setMarkerTool()

    val result = McpToolsMarkdownExporter.generateMarkdownTree(listOf(t))
    assertEquals(
      McpToolsMarkdownExporter.generateMarkdownForTool(t, omitFirstDescriptionLine = true),
      result.getValue("$TREE_TOOLS_SUBDIR/set_marker.md"),
    )
  }

  @Test
  fun generateMarkdownTreePerToolFileDropsFirstDescriptionLineButKeepsBody() {
    val t = startTaskTool(
      description = "Starts a task using the supplied configuration.\nReturns the task handle.",
    )

    val result = McpToolsMarkdownExporter.generateMarkdownTree(listOf(t))
    val index = result.getValue(TREE_INDEX_FILE)
    val perTool = result.getValue("$TREE_TOOLS_SUBDIR/start_task.md")

    assert(index.contains("Starts a task using the supplied configuration.")) {
      "Index should keep the first description line: $index"
    }
    assert(!perTool.contains("Starts a task using the supplied configuration.")) {
      "Per-tool file should drop the first description line (it lives in the index): $perTool"
    }
    assert(perTool.contains("Returns the task handle.")) {
      "Per-tool file should keep the rest of the description body: $perTool"
    }
  }

  @Test
  fun generateMarkdownTreeEmptyListReturnsOnlyIndexHeaderAndLegend() {
    val result = McpToolsMarkdownExporter.generateMarkdownTree(emptyList())

    assertEquals(setOf(TREE_INDEX_FILE), result.keys)
    assertEquals(
      md("# Tools", "", McpToolsMarkdownExporter.TREE_INDEX_LEGEND, ""),
      result.getValue(TREE_INDEX_FILE),
    )
  }

  @Test
  fun generateMarkdownTreeIndexIncludesLegendBlockOnceAndPerToolFilesDoNot() {
    val result = McpToolsMarkdownExporter.generateMarkdownTree(
      listOf(getStateTool(), setMarkerTool()),
    )

    val index = result.getValue(TREE_INDEX_FILE)
    val legendCountInIndex = index.split(McpToolsMarkdownExporter.TREE_INDEX_LEGEND).size - 1
    assertEquals(1, legendCountInIndex, "Legend should appear exactly once in the index: $index")

    for ((path, content) in result) {
      if (path == TREE_INDEX_FILE) continue
      assert(!content.contains(McpToolsMarkdownExporter.TREE_INDEX_LEGEND)) {
        "Per-tool file $path should not contain the legend: $content"
      }
    }
  }

  private val DEFAULT_CATEGORY = McpToolCategory(
    shortName = "GeneralToolset",
    fullyQualifiedName = "com.example.GeneralToolset",
  )

  private fun emptySchema(): McpToolSchema = schema {}

  private fun schema(
    required: Set<String> = emptySet(),
    block: JsonObjectBuilder.() -> Unit,
  ): McpToolSchema = McpToolSchema.ofPropertiesSchema(
    properties = buildJsonObject(block),
    requiredProperties = required,
    definitions = emptyMap(),
  )

  private fun tool(
    name: String,
    category: McpToolCategory = DEFAULT_CATEGORY,
    description: String = "",
    input: McpToolSchema = emptySchema(),
    output: McpToolSchema? = null,
  ): McpTool = object : McpTool {
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
      name = name,
      description = description,
      category = category,
      fullyQualifiedName = "${category.fullyQualifiedName}.$name",
      inputSchema = input,
      outputSchema = output,
    )

    override suspend fun call(args: JsonObject): McpToolCallResult =
      throw NotImplementedError("Markdown exporter never invokes call()")
  }

  /**
   * Build a string by appending each [parts] entry followed by `\n`, mirroring
   * the exporter's use of [StringBuilder.appendLine].
   */
  private fun md(vararg parts: String): String = buildString {
    for (p in parts) appendLine(p)
  }

  private fun assertLineBreakEscaped(description: String) {
    val output = McpToolsMarkdownExporter.generateMarkdownForTool(startTaskTool(description))
    assert(output.contains("Starts a task.<br/>Returns the handle.")) {
      "Expected <br/> separator, got: $output"
    }
  }

  /** No work, no params, no output. Smallest possible tool shape. */
  private fun pingTool(): McpTool = tool(
    name = "ping",
    description = "Performs no work.",
  )

  /** No params, empty output schema present (zero output fields). */
  private fun pingWithEmptyOutputTool(): McpTool = tool(
    name = "ping",
    description = "Performs no work.",
    output = emptySchema(),
  )

  /** Required-only input, no output. Two simple required params. */
  private fun runToTargetTool(): McpTool = tool(
    name = "run_to_target",
    description = "Resumes execution to the specified line.",
    input = schema(required = setOf("sessionId", "line")) {
      putJsonObject("sessionId") {
        put("type", "string")
        put("description", "Active session identifier")
      }
      putJsonObject("line") {
        put("type", "integer")
        put("description", "1-based line number")
      }
    },
  )

  /** Required input + simple output schema. */
  private fun mutateValueTool(): McpTool = tool(
    name = "mutate_value",
    description = "Updates the value at the supplied path.",
    input = schema(required = setOf("path", "newValue")) {
      putJsonObject("path") {
        put("type", "string")
        put("description", "Target path")
      }
      putJsonObject("newValue") {
        put("type", "string")
        put("description", "Replacement value")
      }
    },
    output = schema {
      putJsonObject("previousValue") {
        put("type", "string")
        put("description", "Value prior to update")
      }
    },
  )

  /** Mixed required + optional input. */
  private fun inspectNodeTool(): McpTool = tool(
    name = "inspect_node",
    description = "Inspects a node by path within a session.",
    input = schema(required = setOf("sessionId", "path")) {
      putJsonObject("sessionId") {
        put("type", "string")
        put("description", "Active session identifier")
      }
      putJsonObject("path") {
        put("type", "string")
        put("description", "Dotted path to the node")
      }
      putJsonObject("frameIndex") {
        put("type", "integer")
        put("description", "Frame index to evaluate in")
      }
    },
  )

  /** Enum-valued + nullable-boolean param. */
  private fun setMarkerTool(): McpTool = tool(
    name = "set_marker",
    description = "Creates or updates a marker.",
    input = schema(required = setOf("position")) {
      putJsonObject("position") {
        put("type", "integer")
        put("description", "1-based position")
      }
      putJsonObject("priority") {
        put("type", "string")
        putJsonArray("enum") {
          add("LOW")
          add("MEDIUM")
          add("HIGH")
        }
        put("description", "Priority level")
      }
      putJsonObject("silenced") {
        putJsonArray("type") {
          add("boolean")
          add("null")
        }
        put("description", "Suppresses notifications when true")
      }
    },
  )

  /** Zero input, rich structured output: array-of-objects + nested object. */
  private fun getStateTool(): McpTool = tool(
    name = "get_state",
    description = "Returns the current state of all active tasks.",
    output = schema {
      putJsonObject("entries") {
        put("type", "array")
        putJsonObject("items") {
          put("type", "object")
          putJsonObject("properties") {
            putJsonObject("id") {
              put("type", "string")
              put("description", "Identifier")
            }
            putJsonObject("name") {
              put("type", "string")
              put("description", "Display name")
            }
            putJsonObject("state") {
              put("type", "string")
              putJsonArray("enum") {
                add("RUNNING")
                add("PAUSED")
                add("STOPPED")
              }
              put("description", "Current state")
            }
          }
        }
        put("description", "Active task entries")
      }
      putJsonObject("location") {
        put("type", "object")
        putJsonObject("properties") {
          putJsonObject("path") {
            put("type", "string")
            put("description", "File path")
          }
          putJsonObject("line") {
            put("type", "integer")
            put("description", "1-based line")
          }
          putJsonObject("column") {
            putJsonArray("type") {
              add("integer")
              add("null")
            }
            put("description", "1-based column when known")
          }
        }
        put("description", "Current cursor position")
      }
    },
  )

  /** Configurable-description tool used by multi-line / pipe tests. */
  private fun startTaskTool(
    description: String = "Starts a task using the supplied configuration.",
  ): McpTool = tool(
    name = "start_task",
    description = description,
    input = schema {
      putJsonObject("configurationName") {
        put("type", "string")
        put("description", "Name of an existing configuration")
      }
    },
  )

  /** Array-of-primitive param. */
  private fun filterByTagsTool(): McpTool = tool(
    name = "filter_by_tags",
    description = "Returns entries matching the supplied tags.",
    input = schema {
      putJsonObject("tags") {
        put("type", "array")
        putJsonObject("items") { put("type", "string") }
        put("description", "Tag filter")
      }
    },
  )

  /** Schema property without a type — exporter edge case. */
  private fun typelessParamTool(): McpTool = tool(
    name = "introspect",
    description = "Inspects an opaque payload.",
    input = schema {
      putJsonObject("payload") {
        put("description", "Untyped payload — exporter renders type as N/A")
      }
    },
  )
}
