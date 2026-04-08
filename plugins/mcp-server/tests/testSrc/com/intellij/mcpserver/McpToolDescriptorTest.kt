package com.intellij.mcpserver

import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.impl.util.asToolDescriptor
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
}
