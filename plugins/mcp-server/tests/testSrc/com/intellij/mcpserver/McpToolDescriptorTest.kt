package com.intellij.mcpserver

import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue.FALSE
import com.intellij.mcpserver.annotations.McpToolHintValue.TRUE
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.impl.util.asToolDescriptor
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private interface TitledTool {
  @McpTool(title = "Base title")
  fun tool()
}

private class InheritedTitleTool : TitledTool {
  override fun tool() = Unit
}

private class ResetTitleTool : TitledTool {
  @McpTool(title = "")
  override fun tool() = Unit
}

private class ExplicitTitleTool {
  @McpTool(title = "Explicit title")
  fun tool() = Unit
}

private interface HintedTool {
  @McpTool
  @McpToolHints(readOnlyHint = TRUE, openWorldHint = FALSE)
  fun tool()
}

private class InheritedHintsTool : HintedTool {
  override fun tool() = Unit
}

private class PartiallyOverriddenHintsTool : HintedTool {
  @McpToolHints(openWorldHint = TRUE)
  override fun tool() = Unit
}

private class ResetReadOnlyHintTool : HintedTool {
  @McpToolHints(readOnlyHint = FALSE)
  override fun tool() = Unit
}

class McpToolDescriptorTest {
  @Test
  fun explicit_title_is_exposed_in_descriptor() {
    val descriptor = ExplicitTitleTool::tool.asToolDescriptor()

    assertEquals("Explicit title", descriptor.title)
  }

  @Test
  fun inherited_title_is_used_when_local_annotation_is_missing() {
    val descriptor = InheritedTitleTool::tool.asToolDescriptor()

    assertEquals("Base title", descriptor.title)
  }

  @Test
  fun empty_title_resets_inherited_title() {
    val descriptor = ResetTitleTool::tool.asToolDescriptor()

    assertNull(descriptor.title)
  }

  @Test
  fun inherited_hints_are_exposed_in_descriptor() {
    val descriptor = InheritedHintsTool::tool.asToolDescriptor()

    assertEquals(ToolAnnotations(readOnlyHint = true, openWorldHint = false), descriptor.annotations)
  }

  @Test
  fun local_hints_override_inherited_values_per_property() {
    val descriptor = PartiallyOverriddenHintsTool::tool.asToolDescriptor()

    assertEquals(ToolAnnotations(readOnlyHint = true, openWorldHint = true), descriptor.annotations)
  }

  @Test
  fun false_hint_resets_inherited_true_value() {
    val descriptor = ResetReadOnlyHintTool::tool.asToolDescriptor()

    assertEquals(ToolAnnotations(readOnlyHint = false, openWorldHint = false), descriptor.annotations)
  }
}
