package com.intellij.mcpserver

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
}
