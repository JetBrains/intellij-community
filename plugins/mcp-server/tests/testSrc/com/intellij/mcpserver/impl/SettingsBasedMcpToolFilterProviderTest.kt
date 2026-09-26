// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCategory
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.McpToolInvocationMode
import com.intellij.mcpserver.McpToolSchema
import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

@TestApplication
class SettingsBasedMcpToolFilterProviderTest {
  @AfterEach
  fun tearDown() {
    McpToolFilterSettings.getInstance().toolsFilter = McpToolFilterSettings.DEFAULT_FILTER
  }

  @Test
  fun `advanced mask ignores non-user-configurable tools`() {
    val userTool = fakeTool("user", userConfigurable = true)
    val managedTool = fakeTool("managed", userConfigurable = false)
    val context = McpToolFilterContext(listOf(userTool, managedTool))
    McpToolFilterSettings.getInstance().toolsFilter = "-*"

    SettingsBasedMcpToolFilterProvider().applyFilters(context, null, null, McpToolInvocationMode.DIRECT)

    assertThat(context.routerOnlyTools).containsExactly(managedTool)
  }

  private fun fakeTool(name: String, userConfigurable: Boolean): McpTool {
    return object : McpTool {
      override val isUserConfigurable: Boolean = userConfigurable
      override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = name,
        category = McpToolCategory(shortName = "Test", fullyQualifiedName = "test", isExperimental = false),
        fullyQualifiedName = "test.$name",
        inputSchema = McpToolSchema.ofPropertiesSchema(JsonObject(emptyMap()), emptySet(), emptyMap()),
      )

      override suspend fun call(args: JsonObject): McpToolCallResult = error("Not needed for tests")
    }
  }
}
