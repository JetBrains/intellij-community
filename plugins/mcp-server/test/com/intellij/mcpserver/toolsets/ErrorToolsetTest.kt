@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.ErrorToolset
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import io.kotest.common.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class ErrorToolsetTest : McpToolsetTestBase() {
  @Test
  fun get_current_file_errors() = runBlocking {
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(mainJavaFile, true)
    }
    testMcpTool(
      ErrorToolset::get_current_file_errors.name,
      buildJsonObject {},
      "[]"
    )
  }

  @Test
  fun get_project_problems() = runBlocking {
    testMcpTool(
      ErrorToolset::get_project_problems.name,
      buildJsonObject {},
      "[]"
    )
  }
}
