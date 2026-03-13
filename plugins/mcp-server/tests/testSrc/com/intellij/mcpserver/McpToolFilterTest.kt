package com.intellij.mcpserver

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpToolFilterTest {

  @Test
  fun `AllowAll filter includes all tools`() {
    val filter = McpToolFilter.AllowAll

    assertTrue(filter.shouldInclude("read_file"))
    assertTrue(filter.shouldInclude("write_file"))
    assertTrue(filter.shouldInclude("execute_command"))
    assertTrue(filter.shouldInclude("any_tool_name"))
    assertTrue(filter.shouldInclude(""))
  }

  @Test
  fun `AllowList filter includes allowed tools`() {
    val filter = McpToolFilter.AllowList(setOf("read_file", "write_file", "grep"))

    assertTrue(filter.shouldInclude("read_file"))
    assertTrue(filter.shouldInclude("write_file"))
    assertTrue(filter.shouldInclude("grep"))
  }

  @Test
  fun `AllowList filter excludes non-allowed tools`() {
    val filter = McpToolFilter.AllowList(setOf("read_file", "write_file"))

    assertFalse(filter.shouldInclude("execute_command"))
    assertFalse(filter.shouldInclude("git_commit"))
    assertFalse(filter.shouldInclude("delete_file"))
  }

  @Test
  fun `AllowList filter with empty set excludes all tools`() {
    val filter = McpToolFilter.AllowList(emptySet())

    assertFalse(filter.shouldInclude("read_file"))
    assertFalse(filter.shouldInclude("write_file"))
    assertFalse(filter.shouldInclude("any_tool"))
  }

  @Test
  fun `AllowList filter is case sensitive`() {
    val filter = McpToolFilter.AllowList(setOf("read_file"))

    assertTrue(filter.shouldInclude("read_file"))
    assertFalse(filter.shouldInclude("Read_File"))
    assertFalse(filter.shouldInclude("READ_FILE"))
  }

  @Test
  fun `MaskBased filter with single allow pattern`() {
    val filter = McpToolFilter.MaskBased("read_file")

    assertTrue(filter.shouldInclude("read_file"))
    // Default is allow if no mask matches
    assertTrue(filter.shouldInclude("write_file"))
  }

  @Test
  fun `MaskBased filter with wildcard pattern`() {
    // Note: * matches any characters, including dots
    val filter = McpToolFilter.MaskBased("com.intellij.mcpserver.toolsets.general.*")

    assertTrue(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.read_file"))
    assertTrue(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.write_file"))
    // This also matches because .* matches any characters including dots
    assertTrue(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.nested.tool"))
    // This does NOT match because it doesn't start with the prefix
    assertTrue(filter.shouldInclude("com.intellij.mcpserver.toolsets.vcs.git_commit")) // default allow, no match
  }

  @Test
  fun `MaskBased filter with disallow all and allow specific prefix`() {
    // -* disallows all, then +prefix.* allows tools with that prefix
    val filter = McpToolFilter.MaskBased("-*,+com.intellij.mcpserver.toolsets.general.*")

    assertTrue(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.read_file"))
    assertTrue(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.write_file"))
    assertFalse(filter.shouldInclude("com.intellij.mcpserver.toolsets.vcs.git_commit"))
    assertFalse(filter.shouldInclude("other_tool"))
  }

  @Test
  fun `MaskBased filter with complex pattern`() {
    // -* disallows all, +com.intellij.mcpserver.toolsets.general.* allows general tools,
    // -*.get_file_text_by_path disallows get_file_text_by_path from any package
    val filter = McpToolFilter.MaskBased("-*,+com.intellij.mcpserver.toolsets.general.*,-*.get_file_text_by_path")

    assertTrue(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.read_file"))
    assertTrue(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.write_file"))
    assertFalse(filter.shouldInclude("com.intellij.mcpserver.toolsets.general.get_file_text_by_path"))
    assertFalse(filter.shouldInclude("com.intellij.mcpserver.toolsets.vcs.git_commit"))
  }

  @Test
  fun `MaskBased filter with explicit allow prefix`() {
    val filter = McpToolFilter.MaskBased("+read_file,+write_file")

    assertTrue(filter.shouldInclude("read_file"))
    assertTrue(filter.shouldInclude("write_file"))
    // Default is allow if no mask matches
    assertTrue(filter.shouldInclude("other_tool"))
  }

  @Test
  fun `MaskBased filter with explicit disallow prefix`() {
    val filter = McpToolFilter.MaskBased("-read_file,-write_file")

    assertFalse(filter.shouldInclude("read_file"))
    assertFalse(filter.shouldInclude("write_file"))
    // Default is allow if no mask matches
    assertTrue(filter.shouldInclude("other_tool"))
  }

  @Test
  fun `MaskBased filter last matching mask wins`() {
    // First allows, then disallows - last one wins
    val filter = McpToolFilter.MaskBased("+read_file,-read_file")

    assertFalse(filter.shouldInclude("read_file"))
  }

  @Test
  fun `MaskBased fromMaskList returns AllowAll for empty string`() {
    val filter = McpToolFilter.MaskBased.fromMaskList("")

    assertTrue(filter is McpToolFilter.AllowAll)
    assertTrue(filter.shouldInclude("any_tool"))
  }

  @Test
  fun `MaskBased fromMaskList returns AllowAll for blank string`() {
    val filter = McpToolFilter.MaskBased.fromMaskList("   ")

    assertTrue(filter is McpToolFilter.AllowAll)
  }

  @Test
  fun `MaskBased fromMaskList returns MaskBased for non-empty string`() {
    val filter = McpToolFilter.MaskBased.fromMaskList("-*,+read_file")

    assertTrue(filter is McpToolFilter.MaskBased)
    assertTrue(filter.shouldInclude("read_file"))
    assertFalse(filter.shouldInclude("write_file"))
  }

  @Test
  fun `MaskBased filter handles whitespace in mask list`() {
    val filter = McpToolFilter.MaskBased(" -* , +read_file , +write_file ")

    assertTrue(filter.shouldInclude("read_file"))
    assertTrue(filter.shouldInclude("write_file"))
    assertFalse(filter.shouldInclude("other_tool"))
  }
}
