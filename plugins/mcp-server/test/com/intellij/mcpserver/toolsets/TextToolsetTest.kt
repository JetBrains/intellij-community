@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.TextToolset
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class TextToolsetTest : McpToolsetTestBase() {
  @Test
  fun get_file_text_by_path() = runBlocking {
    testMcpTool(
      TextToolset::get_file_text_by_path.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(testJavaFile.name))
      },
      "Test.java content"
    )
  }

  @Test
  fun replace_file_text_by_path() = runBlocking {
    testMcpTool(
      TextToolset::replace_text_in_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(mainJavaFile.name))
        put("text", JsonPrimitive("updated content"))
      },
      "ok"
    )
  }
}