// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCategory
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.McpToolSchema
import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpToolDisallowListSettings.ToolState
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

@TestApplication
class McpToolFilterConfigurableTest {
  @AfterEach
  fun tearDown() {
    McpToolDisallowListSettings.getInstance().toolStates = emptyMap()

    val filterSettings = McpToolFilterSettings.getInstance()
    filterSettings.toolsFilter = McpToolFilterSettings.DEFAULT_FILTER
    filterSettings.invocationMode = McpSessionInvocationMode.DIRECT
  }

  @Test
  fun `untouched configurable is not modified when no overrides are stored`() {
    val configurable = McpToolFilterConfigurable()

    try {
      configurable.createComponent()

      assertThat(configurable.isModified()).isFalse()
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  fun `stored tool overrides do not mark configurable modified`() {
    val toolKey = McpServerService.getInstance()
      .getMcpToolsFiltered(useFiltersFromEP = false, excludeProviders = emptySet())
      .first()
      .descriptor
      .fullyQualifiedName

    McpToolDisallowListSettings.getInstance().toolStates = mapOf(
      toolKey to ToolState(enabled = false, routerOnly = false),
    )

    val configurable = McpToolFilterConfigurable()

    try {
      configurable.createComponent()

      assertThat(configurable.isModified()).isFalse()
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  fun `hidden on demand changes are ignored by modified state and apply`() {
    val toolKey = McpServerService.getInstance()
      .getMcpToolsFiltered(useFiltersFromEP = false, excludeProviders = emptySet())
      .first()
      .descriptor
      .fullyQualifiedName

    McpToolDisallowListSettings.getInstance().toolStates = mapOf(
      toolKey to ToolState(enabled = true, routerOnly = true),
    )

    val configurable = McpToolFilterConfigurable()

    try {
      configurable.createComponent()
      editableToolStates(configurable)[toolKey] = ToolState(enabled = true, routerOnly = false)

      assertThat(configurable.isModified()).isFalse()

      configurable.apply()

      assertThat(McpToolDisallowListSettings.getInstance().toolStates[toolKey])
        .isNull()
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  fun `tool state toggled back to default clears modified state`() {
    val toolKey = McpServerService.getInstance()
      .getMcpToolsFiltered(useFiltersFromEP = false, excludeProviders = emptySet())
      .first()
      .descriptor
      .fullyQualifiedName

    val configurable = McpToolFilterConfigurable()

    try {
      configurable.createComponent()

      editableToolStates(configurable)[toolKey] = ToolState(enabled = false, routerOnly = true)
      assertThat(configurable.isModified()).isTrue()

      editableToolStates(configurable)[toolKey] = ToolState()
      assertThat(configurable.isModified()).isFalse()
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  fun `enabled checkbox toggled back clears modified state`() {
    val toolKey = McpServerService.getInstance()
      .getMcpToolsFiltered(useFiltersFromEP = false, excludeProviders = emptySet())
      .first()
      .descriptor
      .fullyQualifiedName

    val configurable = McpToolFilterConfigurable()

    try {
      configurable.createComponent()

      val enabledCheckBox = toolEnabledCheckBox(configurable, toolKey)
      enabledCheckBox.doClick()
      assertThat(configurable.isModified()).isTrue()

      enabledCheckBox.doClick()
      assertThat(configurable.isModified()).isFalse()
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  fun `category on demand state ignores disabled tools`() {
    val configurable = McpToolFilterConfigurable()
    val enabledTool = testTool(name = "duplicate_name", fullyQualifiedName = "test.enabled")
    val disabledTool = testTool(name = "duplicate_name", fullyQualifiedName = "test.disabled")

    try {
      editableToolStates(configurable)[enabledTool.descriptor.fullyQualifiedName] = ToolState(enabled = true, routerOnly = true)
      editableToolStates(configurable)[disabledTool.descriptor.fullyQualifiedName] = ToolState(enabled = false, routerOnly = false)

      assertThat(calculateCategoryRouterOnlyState(configurable, listOf(enabledTool, disabledTool)))
        .isEqualTo(ThreeStateCheckBox.State.SELECTED)

      editableToolStates(configurable)[enabledTool.descriptor.fullyQualifiedName] = ToolState(enabled = false, routerOnly = true)

      assertThat(calculateCategoryRouterOnlyState(configurable, listOf(enabledTool, disabledTool)))
        .isEqualTo(ThreeStateCheckBox.State.NOT_SELECTED)
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  fun `description render model rewraps when width shrinks`() {
    val description = "Alpha beta gamma delta epsilon zeta eta theta iota kappa"

    val wideModel = buildDescriptionRenderModel(
      description = description,
      collapsedTextWidth = 200,
      expandedTextWidth = 20,
      textWidth = String::length,
    )
    val narrowModel = buildDescriptionRenderModel(
      description = description,
      collapsedTextWidth = 80,
      expandedTextWidth = 10,
      textWidth = String::length,
    )

    assertThat(narrowModel.expandedRows.count())
      .isGreaterThan(wideModel.expandedRows.count())
    assertThat(narrowModel.expandedRows)
      .allSatisfy { row -> assertThat(row.length).isLessThanOrEqualTo(10) }
  }

  @Test
  fun `description render model keeps preferred width bounded`() {
    val model = buildDescriptionRenderModel(
      description = "supercalifragilisticexpialidocious",
      collapsedTextWidth = 8,
      expandedTextWidth = 6,
      textWidth = String::length,
    )

    assertThat(model.collapsedPreview.length).isLessThanOrEqualTo(8)
    assertThat(model.expandedRows)
      .allSatisfy { row -> assertThat(row.length).isLessThanOrEqualTo(6) }
  }

  @Suppress("UNCHECKED_CAST")
  private fun editableToolStates(configurable: McpToolFilterConfigurable): MutableMap<String, ToolState> {
    val field = McpToolFilterConfigurable::class.java.getDeclaredField("allToolStates")
    field.isAccessible = true
    return field.get(configurable) as MutableMap<String, ToolState>
  }

  @Suppress("UNCHECKED_CAST")
  private fun toolEnabledCheckBox(configurable: McpToolFilterConfigurable, toolName: String): JBCheckBox {
    val field = McpToolFilterConfigurable::class.java.getDeclaredField("toolRowViews")
    field.isAccessible = true
    val toolRowViews = field.get(configurable) as Map<String, Any>
    val toolRowView = toolRowViews.getValue(toolName)
    val enabledCheckBoxField = toolRowView.javaClass.getDeclaredField("enabledCheckBox")
    enabledCheckBoxField.isAccessible = true
    return enabledCheckBoxField.get(toolRowView) as JBCheckBox
  }

  private fun calculateCategoryRouterOnlyState(configurable: McpToolFilterConfigurable, tools: List<McpTool>): ThreeStateCheckBox.State {
    val method = McpToolFilterConfigurable::class.java.getDeclaredMethod("calculateCategoryRouterOnlyState", List::class.java)
    method.isAccessible = true
    return method.invoke(configurable, tools) as ThreeStateCheckBox.State
  }

  private fun testTool(name: String, fullyQualifiedName: String): McpTool {
    return object : McpTool {
      override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = name,
        category = McpToolCategory(
          shortName = "Test",
          fullyQualifiedName = "test.category",
          isExperimental = false,
        ),
        fullyQualifiedName = fullyQualifiedName,
        inputSchema = McpToolSchema.ofPropertiesSchema(buildJsonObject { }, emptySet(), emptyMap()),
      )

      override suspend fun call(args: JsonObject): McpToolCallResult {
        error("Not needed for tests")
      }
    }
  }
}
