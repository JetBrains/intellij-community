@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.AnalysisToolset
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import io.kotest.common.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class AnalysisToolsetTest : McpToolsetTestBase() {
  @Test
  fun get_file_problems() = runBlocking {
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(mainJavaFile, true)
    }
    testMcpTool(
      AnalysisToolset::get_file_problems.name,
      buildJsonObject {},
      "[]"
    )
  }

  @Test
  fun get_project_problems() = runBlocking {
    testMcpTool(
      AnalysisToolset::get_project_problems.name,
      buildJsonObject {},
      "[]"
    )
  }

  @Test
  fun get_project_modules() = runBlocking {
    testMcpTool(
      AnalysisToolset::get_project_modules.name,
      buildJsonObject {},
      """{"modules":[{"name":"testModule","type":"JAVA_MODULE"}]}"""
    )
  }

  @Test
  fun get_project_dependencies() = runBlocking {
    testMcpTool(
      AnalysisToolset::get_project_dependencies.name,
      buildJsonObject {},
      """{"dependencies":[]}"""
    )
  }
}
