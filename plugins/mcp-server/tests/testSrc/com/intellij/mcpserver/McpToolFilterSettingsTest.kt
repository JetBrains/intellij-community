@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable

@Testable
@TestApplication
class McpToolFilterSettingsTest {
  @AfterEach
  fun tearDown() {
    // Reset filter to default after each test
    McpToolFilterSettings.getInstance().toolsFilter = McpToolFilterSettings.DEFAULT_FILTER
  }

  @Test
  fun `default filter allows all tools`() {
    McpToolFilterSettings.getInstance().toolsFilter = "*"

    val tools = McpServerService.getInstance().getMcpTools()

    assertThat(tools).isNotEmpty()
    assertThat(tools).anyMatch { it.descriptor.name == "read_file" }
    assertThat(tools).anyMatch { it.descriptor.name == "replace_text_in_file" }
  }

  @Test
  fun `filter excludes all then includes specific package`() {
    // Exclude all, then include only tools from general package
    McpToolFilterSettings.getInstance().toolsFilter = "-*,+com.intellij.mcpserver.toolsets.general.*"

    val tools = McpServerService.getInstance().getMcpTools()

    assertThat(tools).isNotEmpty()
    // All tools should be from the general package
    tools.forEach { tool ->
      assertThat(tool.descriptor.fullyQualifiedName).startsWith("com.intellij.mcpserver.toolsets.general.")
    }
  }

  @Test
  fun `filter excludes specific tool`() {
    // Allow all, then exclude specific tool
    McpToolFilterSettings.getInstance().toolsFilter = "*,-*.read_file"

    val tools = McpServerService.getInstance().getMcpTools()

    assertThat(tools).isNotEmpty()
    assertThat(tools).noneMatch { it.descriptor.name == "read_file" }
    assertThat(tools).anyMatch { it.descriptor.name == "replace_text_in_file" }
  }

  @Test
  fun `complex filter excludes all then includes package then excludes specific tool`() {
    // Exclude all, include general package, then exclude read_file
    McpToolFilterSettings.getInstance().toolsFilter = "-*,+com.intellij.mcpserver.toolsets.general.*,-*.read_file"

    val tools = McpServerService.getInstance().getMcpTools()

    assertThat(tools).isNotEmpty()
    // All tools should be from the general package
    tools.forEach { tool ->
      assertThat(tool.descriptor.fullyQualifiedName).startsWith("com.intellij.mcpserver.toolsets.general.")
    }
    // But not read_file
    assertThat(tools).noneMatch { it.descriptor.name == "read_file" }
    assertThat(tools).anyMatch { it.descriptor.name == "replace_text_in_file" }
  }

  @Test
  fun `filter excludes all tools`() {
    McpToolFilterSettings.getInstance().toolsFilter = "-*"

    val tools = McpServerService.getInstance().getMcpTools()

    assertThat(tools).isEmpty()
  }

  @Test
  fun `filter includes only TextToolset tools`() {
    McpToolFilterSettings.getInstance().toolsFilter = "-*,+com.intellij.mcpserver.toolsets.general.TextToolset.*"

    val tools = McpServerService.getInstance().getMcpTools()

    assertThat(tools).isNotEmpty()
    tools.forEach { tool ->
      assertThat(tool.descriptor.fullyQualifiedName).startsWith("com.intellij.mcpserver.toolsets.general.TextToolset.")
    }
    assertThat(tools).hasSize(1)
    assertThat(tools).allMatch { it.descriptor.name == "replace_text_in_file" }
  }

  @Test
  fun `filter is applied in order`() {
    // This tests that filters are applied in sequence:
    // 1. Start with all tools allowed
    // 2. Exclude all
    // 3. Include general package
    // 4. Exclude read_file
    // 5. Include read_file again (should be present in final result)
    McpToolFilterSettings.getInstance().toolsFilter = "-*,+com.intellij.mcpserver.toolsets.general.*,-*.read_file,+*.read_file"

    val tools = McpServerService.getInstance().getMcpTools()

    assertThat(tools).isNotEmpty()
    assertThat(tools).anyMatch { it.descriptor.name == "read_file" }
  }

  @Test
  fun `filter with spaces is trimmed correctly`() {
    // Test that spaces around commas are handled correctly
    McpToolFilterSettings.getInstance().toolsFilter = "-*, +com.intellij.mcpserver.toolsets.general.TextToolset.*"

    val tools = McpServerService.getInstance().getMcpTools()

    assertThat(tools).isNotEmpty()
    tools.forEach { tool ->
      assertThat(tool.descriptor.fullyQualifiedName).startsWith("com.intellij.mcpserver.toolsets.general.TextToolset.")
    }
  }
}
