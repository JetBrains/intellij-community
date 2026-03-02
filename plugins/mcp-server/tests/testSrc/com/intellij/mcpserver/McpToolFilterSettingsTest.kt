@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.impl.ENABLE_APPLY_PATCH_TOOL_REGISTRY_KEY
import com.intellij.mcpserver.impl.ENABLE_GIT_STATUS_TOOL_REGISTRY_KEY
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GIT_STATUS_TOOL_NAME = "git_status"
private const val APPLY_PATCH_TOOL_NAME = "apply_patch"

@Testable
@TestApplication
class McpToolFilterSettingsTest {
  @AfterEach
  fun tearDown() {
    // Reset filter to default after each test
    McpToolFilterSettings.getInstance().toolsFilter = McpToolFilterSettings.DEFAULT_FILTER
  }

  @Test
  fun `registry keys hide git_status and apply_patch by default`() {
    val tools = McpServerService.getInstance().getMcpTools()

    assertFalse(
      tools.any { it.descriptor.name == GIT_STATUS_TOOL_NAME },
      "Should hide $GIT_STATUS_TOOL_NAME when the key is false"
    )
    assertFalse(
      tools.any { it.descriptor.name == APPLY_PATCH_TOOL_NAME },
      "Should hide $APPLY_PATCH_TOOL_NAME when the key is false"
    )
  }

  @RegistryKey(key = ENABLE_GIT_STATUS_TOOL_REGISTRY_KEY, value = "true")
  @RegistryKey(key = ENABLE_APPLY_PATCH_TOOL_REGISTRY_KEY, value = "true")
  @Test
  fun `registry keys can enable git_status and apply_patch`() {
    val tools = McpServerService.getInstance().getMcpTools()

    assertTrue(
      tools.any { it.descriptor.name == GIT_STATUS_TOOL_NAME },
      "Should expose $GIT_STATUS_TOOL_NAME when the key is true"
    )
    assertTrue(
      tools.any { it.descriptor.name == APPLY_PATCH_TOOL_NAME },
      "Should expose $APPLY_PATCH_TOOL_NAME when the key is true"
    )
  }

  @Test
  fun `registry keys hard-disable tools even if filters include them`() {
    McpToolFilterSettings.getInstance().toolsFilter = "-*,+*.$GIT_STATUS_TOOL_NAME,+*.$APPLY_PATCH_TOOL_NAME"

    val tools = McpServerService.getInstance().getMcpTools()

    assertFalse(
      tools.any { it.descriptor.name == GIT_STATUS_TOOL_NAME },
      "Should keep $GIT_STATUS_TOOL_NAME hidden when registry key is false"
    )
    assertFalse(
      tools.any { it.descriptor.name == APPLY_PATCH_TOOL_NAME },
      "Should keep $APPLY_PATCH_TOOL_NAME hidden when registry key is false"
    )
  }

  @Test
  fun `default filter allows all tools`() {
    McpToolFilterSettings.getInstance().toolsFilter = "*"

    val tools = McpServerService.getInstance().getMcpTools()

    assertTrue(tools.isNotEmpty(), "Tools list should not be empty")
    assertTrue(tools.any { it.descriptor.name == "get_file_text_by_path" }, "Should contain get_file_text_by_path tool")
    assertTrue(tools.any { it.descriptor.name == "replace_text_in_file" }, "Should contain replace_text_in_file tool")
  }

  @Test
  fun `filter excludes all then includes specific package`() {
    // Exclude all, then include only tools from general package
    McpToolFilterSettings.getInstance().toolsFilter = "-*,+com.intellij.mcpserver.toolsets.general.*"

    val tools = McpServerService.getInstance().getMcpTools()

    assertTrue(tools.isNotEmpty(), "Tools list should not be empty")
    // All tools should be from the general package
    tools.forEach { tool ->
      assertTrue(
        tool.descriptor.fullyQualifiedName.startsWith("com.intellij.mcpserver.toolsets.general."),
        "Tool ${tool.descriptor.fullyQualifiedName} should be from the general package"
      )
    }
  }

  @Test
  fun `filter excludes specific tool`() {
    // Allow all, then exclude specific tool
    McpToolFilterSettings.getInstance().toolsFilter = "*,-*.get_file_text_by_path"

    val tools = McpServerService.getInstance().getMcpTools()

    assertTrue(tools.isNotEmpty(), "Tools list should not be empty")
    assertFalse(
      tools.any { it.descriptor.name == "get_file_text_by_path" },
      "Should not contain get_file_text_by_path tool"
    )
    assertTrue(
      tools.any { it.descriptor.name == "replace_text_in_file" },
      "Should still contain replace_text_in_file tool"
    )
  }

  @Test
  fun `complex filter excludes all then includes package then excludes specific tool`() {
    // Exclude all, include general package, then exclude get_file_text_by_path
    McpToolFilterSettings.getInstance().toolsFilter = "-*,+com.intellij.mcpserver.toolsets.general.*,-*.get_file_text_by_path"

    val tools = McpServerService.getInstance().getMcpTools()

    assertTrue(tools.isNotEmpty(), "Tools list should not be empty")
    // All tools should be from the general package
    tools.forEach { tool ->
      assertTrue(
        tool.descriptor.fullyQualifiedName.startsWith("com.intellij.mcpserver.toolsets.general."),
        "Tool ${tool.descriptor.fullyQualifiedName} should be from the general package"
      )
    }
    // But not get_file_text_by_path
    assertFalse(
      tools.any { it.descriptor.name == "get_file_text_by_path" },
      "Should not contain get_file_text_by_path tool"
    )
    assertTrue(
      tools.any { it.descriptor.name == "replace_text_in_file" },
      "Should still contain replace_text_in_file tool"
    )
  }

  @Test
  fun `filter excludes all tools`() {
    McpToolFilterSettings.getInstance().toolsFilter = "-*"

    val tools = McpServerService.getInstance().getMcpTools()

    assertTrue(tools.isEmpty(), "Tools list should be empty when all tools are excluded")
  }

  @Test
  fun `filter includes only TextToolset tools`() {
    McpToolFilterSettings.getInstance().toolsFilter = "-*,+com.intellij.mcpserver.toolsets.general.TextToolset.*"

    val tools = McpServerService.getInstance().getMcpTools()

    assertTrue(tools.isNotEmpty(), "Tools list should not be empty")
    tools.forEach { tool ->
      assertTrue(
        tool.descriptor.fullyQualifiedName.startsWith("com.intellij.mcpserver.toolsets.general.TextToolset."),
        "Tool ${tool.descriptor.fullyQualifiedName} should be from TextToolset"
      )
    }
    assertTrue(
      tools.any { it.descriptor.name == "get_file_text_by_path" },
      "Should contain get_file_text_by_path from TextToolset"
    )
  }

  @Test
  fun `filter is applied in order`() {
    // This tests that filters are applied in sequence:
    // 1. Start with all tools allowed
    // 2. Exclude all
    // 3. Include general package
    // 4. Exclude get_file_text_by_path
    // 5. Include get_file_text_by_path again (should be present in final result)
    McpToolFilterSettings.getInstance().toolsFilter = "-*,+com.intellij.mcpserver.toolsets.general.*,-*.get_file_text_by_path,+*.get_file_text_by_path"

    val tools = McpServerService.getInstance().getMcpTools()

    assertTrue(tools.isNotEmpty(), "Tools list should not be empty")
    assertTrue(
      tools.any { it.descriptor.name == "get_file_text_by_path" },
      "Should contain get_file_text_by_path since it was included last"
    )
  }

  @Test
  fun `filter with spaces is trimmed correctly`() {
    // Test that spaces around commas are handled correctly
    McpToolFilterSettings.getInstance().toolsFilter = "-*, +com.intellij.mcpserver.toolsets.general.TextToolset.*"

    val tools = McpServerService.getInstance().getMcpTools()

    assertTrue(tools.isNotEmpty(), "Tools list should not be empty")
    tools.forEach { tool ->
      assertTrue(
        tool.descriptor.fullyQualifiedName.startsWith("com.intellij.mcpserver.toolsets.general.TextToolset."),
        "Tool ${tool.descriptor.fullyQualifiedName} should be from TextToolset"
      )
    }
  }
}
