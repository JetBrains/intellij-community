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
}
