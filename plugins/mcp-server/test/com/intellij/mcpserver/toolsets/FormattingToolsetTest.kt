@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.FormattingToolset
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class FormattingToolsetTest : McpToolsetTestBase() {
  @Test
  fun reformat_file() = runBlocking {
    testMcpTool(
      FormattingToolset::reformat_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(mainJavaFile.name))
      },
      "ok"
    )
  }
}
