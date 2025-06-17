@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.FormattingToolset
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import io.kotest.common.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class FormattingToolsetTest : McpToolsetTestBase() {
  @Test
  fun reformat_current_file() = runBlocking {
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(mainJavaFile, true)
    }
    testMcpTool(
      FormattingToolset::reformat_current_file.name,
      buildJsonObject {},
      "ok"
    )
  }

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
