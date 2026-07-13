package com.intellij.mcpserver

import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.McpToolFilterProvider.McpToolState
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class McpToolFilterTest {

  @Test
  fun `AllowAll filter includes all tools`() {
    val filter = McpToolFilter.AllowAll

    assertThat(filter.shouldInclude("read_file")).isTrue()
    assertThat(filter.shouldInclude("write_file")).isTrue()
    assertThat(filter.shouldInclude("execute_command")).isTrue()
    assertThat(filter.shouldInclude("any_tool_name")).isTrue()
    assertThat(filter.shouldInclude("")).isTrue()
  }

  @Test
  fun `AllowList filter includes allowed tools`() {
    val filter = McpToolFilter.AllowList(setOf("read_file", "write_file", "grep"))

    assertThat(filter.shouldInclude("read_file")).isTrue()
    assertThat(filter.shouldInclude("write_file")).isTrue()
    assertThat(filter.shouldInclude("grep")).isTrue()
  }

  @Test
  fun `AllowList filter excludes non-allowed tools`() {
    val filter = McpToolFilter.AllowList(setOf("read_file", "write_file"))

    assertThat(filter.shouldInclude("execute_command")).isFalse()
    assertThat(filter.shouldInclude("git_commit")).isFalse()
    assertThat(filter.shouldInclude("delete_file")).isFalse()
  }

  @Test
  fun `AllowList filter with empty set excludes all tools`() {
    val filter = McpToolFilter.AllowList(emptySet())

    assertThat(filter.shouldInclude("read_file")).isFalse()
    assertThat(filter.shouldInclude("write_file")).isFalse()
    assertThat(filter.shouldInclude("any_tool")).isFalse()
  }

  @Test
  fun `AllowList filter is case sensitive`() {
    val filter = McpToolFilter.AllowList(setOf("read_file"))

    assertThat(filter.shouldInclude("read_file")).isTrue()
    assertThat(filter.shouldInclude("Read_File")).isFalse()
    assertThat(filter.shouldInclude("READ_FILE")).isFalse()
  }

  @Test
  fun `MaskBased filter with single allow pattern`() {
    val filter = McpToolFilter.MaskBased("read_file")

    assertThat(filter.shouldInclude("read_file")).isTrue()
    // Default is allow if no mask matches
    assertThat(filter.shouldInclude("write_file")).isTrue()
  }

  @Test
  fun `MaskBased filter with wildcard pattern`() {
    // Note: * matches any characters, including dots
    val filter = McpToolFilter.MaskBased("com.intellij.mcpserver.toolsets.general.*")

    assertThat(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.read_file")).isTrue()
    assertThat(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.write_file")).isTrue()
    // This also matches because .* matches any characters including dots
    assertThat(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.nested.tool")).isTrue()
    // This does NOT match because it doesn't start with the prefix
    assertThat(filter.shouldInclude("com.intellij.mcpserver.toolsets.vcs.git_commit")).isTrue() // default allow, no match
  }

  @Test
  fun `MaskBased filter with disallow all and allow specific prefix`() {
    // -* disallows all, then +prefix.* allows tools with that prefix
    val filter = McpToolFilter.MaskBased("-*,+com.intellij.mcpserver.toolsets.general.*")

    assertThat(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.read_file")).isTrue()
    assertThat(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.write_file")).isTrue()
    assertThat(filter.shouldInclude("com.intellij.mcpserver.toolsets.vcs.git_commit")).isFalse()
    assertThat(filter.shouldInclude("other_tool")).isFalse()
  }

  @Test
  fun `MaskBased filter with complex pattern`() {
    // -* disallows all, +com.intellij.mcpserver.toolsets.general.* allows general tools,
    // -*.read_file disallows read_file from any package
    val filter = McpToolFilter.MaskBased("-*,+com.intellij.mcpserver.toolsets.general.*,-*.read_file")

    assertThat(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.read_file")).isFalse()
    assertThat(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.write_file")).isTrue()
    assertThat(filter.shouldInclude("com.intellij.mcpserver.toolsets.vcs.git_commit")).isFalse()
  }

  @Test
  fun `MaskBased filter with explicit allow prefix`() {
    val filter = McpToolFilter.MaskBased("+read_file,+write_file")

    assertThat(filter.shouldInclude("read_file")).isTrue()
    assertThat(filter.shouldInclude("write_file")).isTrue()
    // Default is allow if no mask matches
    assertThat(filter.shouldInclude("other_tool")).isTrue()
  }

  @Test
  fun `MaskBased filter with explicit disallow prefix`() {
    val filter = McpToolFilter.MaskBased("-read_file,-write_file")

    assertThat(filter.shouldInclude("read_file")).isFalse()
    assertThat(filter.shouldInclude("write_file")).isFalse()
    // Default is allow if no mask matches
    assertThat(filter.shouldInclude("other_tool")).isTrue()
  }

  @Test
  fun `MaskBased filter last matching mask wins`() {
    // First allows, then disallows - last one wins
    val filter = McpToolFilter.MaskBased("+read_file,-read_file")

    assertThat(filter.shouldInclude("read_file")).isFalse()
  }

  @Test
  fun `MaskBased fromMaskList returns prohibit-all filter for empty string`() {
    val filter = McpToolFilter.MaskBased.fromMaskList("")

    assertThat(filter).isInstanceOf(McpToolFilter.ProhibitAll::class.java)
  }

  @Test
  fun `MaskBased fromMaskList returns prohibit-all filter for blank string`() {
    val filter = McpToolFilter.MaskBased.fromMaskList("   ")

    assertThat(filter).isInstanceOf(McpToolFilter.ProhibitAll::class.java)
  }

  @Test
  fun `MaskBased fromMaskList returns MaskBased for non-empty string`() {
    val filter = McpToolFilter.MaskBased.fromMaskList("-*,+read_file")

    assertThat(filter).isInstanceOf(McpToolFilter.MaskBased::class.java)
    val maskBased = filter as McpToolFilter.MaskBased
    assertThat(maskBased.shouldInclude("read_file")).isTrue()
    assertThat(maskBased.shouldInclude("write_file")).isFalse()
  }

  @Test
  fun `MaskBased filter handles whitespace in mask list`() {
    val filter = McpToolFilter.MaskBased(" -* , +read_file , +write_file ")

    assertThat(filter.shouldInclude("read_file")).isTrue()
    assertThat(filter.shouldInclude("write_file")).isTrue()
    assertThat(filter.shouldInclude("other_tool")).isFalse()
  }

  // --- McpToolFilterContext tests ---

  @Test
  fun `updateState sets enabled only without affecting routerOnly`() {
    val tool = fakeTool("read_file")
    val context = McpToolFilterContext(listOf(tool))

    context.updateState(enabled = false, predicate = { it == tool })

    assertThat(context.onTools).doesNotContain(tool)
    assertThat(context.routerOnlyTools).doesNotContain(tool)
    // routerOnly should remain default (true), so the tool is neither onTools nor routerOnlyTools
    // because enabled=false removes it from both sets
  }

  @Test
  fun `updateState sets enabled and routerOnly independently`() {
    val tool = fakeTool("read_file")
    val context = McpToolFilterContext(listOf(tool))

    context.updateState(enabled = true, routerOnly = false, predicate = { it == tool })

    assertThat(context.onTools).contains(tool)
    assertThat(context.routerOnlyTools).doesNotContain(tool)
  }

  @Test
  fun `updateState second call can change routerOnly after enabled is already set`() {
    val tool = fakeTool("read_file")
    val context = McpToolFilterContext(listOf(tool))

    // First call: disable the tool (enabled=false, routerOnly stays default true)
    context.updateState(enabled = false, predicate = { it == tool })
    assertThat(context.onTools).doesNotContain(tool)
    assertThat(context.routerOnlyTools).doesNotContain(tool)

    // Second call: re-enable but keep routerOnly=true
    context.updateState(enabled = true, predicate = { it == tool })
    assertThat(context.onTools).doesNotContain(tool)
    assertThat(context.routerOnlyTools).contains(tool)
  }

  @Test
  fun `updateState partial fields does not prevent subsequent provider from setting other field`() {
    val toolA = fakeTool("tool_a")
    val toolB = fakeTool("tool_b")
    val context = McpToolFilterContext(listOf(toolA, toolB))

    // Provider 1: sets enabled=false for toolA
    context.updateState(enabled = false, predicate = { it == toolA })

    // Provider 2: sets enabled=false AND routerOnly=false for toolA
    // The fix ensures this still works — the skip logic no longer blocks
    // because it now checks final values rather than individual fields
    context.updateState(enabled = false, routerOnly = false, predicate = { it == toolA })

    // toolA should not appear in either set (disabled)
    assertThat(context.onTools).doesNotContain(toolA)
    assertThat(context.routerOnlyTools).doesNotContain(toolA)

    // toolB should be unaffected — still at defaults
    assertThat(context.onTools).doesNotContain(toolB)
    assertThat(context.routerOnlyTools).contains(toolB)
  }

  @Test
  fun `updateState with null values preserves existing state`() {
    val tool = fakeTool("read_file")
    val context = McpToolFilterContext(listOf(tool))

    // First: set enabled=true, routerOnly=false
    context.updateState(enabled = true, routerOnly = false, predicate = { it == tool })
    assertThat(context.onTools).contains(tool)

    // Second: only change enabled to false; routerOnly should stay false
    context.updateState(enabled = false, predicate = { it == tool })
    assertThat(context.onTools).doesNotContain(tool)
    assertThat(context.routerOnlyTools).doesNotContain(tool)
    // routerOnly was false and stayed false, so tool is not in routerOnlyTools either
  }

  @Test
  fun `updateState with McpToolState object sets both fields`() {
    val tool = fakeTool("read_file")
    val context = McpToolFilterContext(listOf(tool))

    context.updateState(McpToolState(enabled = true, routerOnly = false), predicate = { it == tool })

    assertThat(context.onTools).contains(tool)
    assertThat(context.routerOnlyTools).doesNotContain(tool)
  }

  private fun fakeTool(name: String): McpTool {
    return object : McpTool {
      override val descriptor = McpToolDescriptor(
        name = name,
        description = name,
        fullyQualifiedName = "test.$name",
        category = McpToolCategory(shortName = "Test", fullyQualifiedName = "test", isExperimental = false),
        inputSchema = McpToolSchema.ofPropertiesSchema(
          JsonObject(emptyMap()), emptySet(), emptyMap()
        ),
      )
      override suspend fun call(args: JsonObject): McpToolCallResult =
        error("not needed")

      override fun toString(): String = "McpTool $name"
    }
  }
}
