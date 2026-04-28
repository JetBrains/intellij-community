// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.settings.ToolCategoryGroup
import com.intellij.mcpserver.settings.buildCategoryGroups
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable

@Testable
@TestApplication
class McpToolFilterConfigurableTest {
  @Test
  fun `tools are grouped by sorted category and tool name`() {
    val groups = buildCategoryGroups(
      listOf(
        testTool("zeta", categoryName = "General"),
        testTool("alpha", categoryName = "General"),
        testTool("beta", categoryName = "Editing"),
      ),
    )

    assertThat(groups.map { it.category.shortName }).containsExactly("Editing", "General")
    assertThat(groups.toolNamesFor("Editing")).containsExactly("beta")
    assertThat(groups.toolNamesFor("General")).containsExactly("alpha", "zeta")
  }

  private fun testTool(
    name: String,
    experimental: Boolean = false,
    categoryName: String = if (experimental) "Experimental" else "Stable",
  ): McpTool {
    return object : McpTool {
      override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = name,
        category = McpToolCategory(
          shortName = categoryName,
          fullyQualifiedName = "test.${categoryName.lowercase()}",
          isExperimental = experimental,
        ),
        fullyQualifiedName = "test.$name",
        inputSchema = McpToolSchema.ofPropertiesSchema(buildJsonObject { }, emptySet(), emptyMap()),
      )

      override suspend fun call(args: JsonObject): McpToolCallResult {
        error("Not needed for tests")
      }
    }
  }

  private fun List<ToolCategoryGroup>.toolNamesFor(categoryName: String): List<String> {
    return single { it.category.shortName == categoryName }.tools.map { it.descriptor.name }
  }
}
