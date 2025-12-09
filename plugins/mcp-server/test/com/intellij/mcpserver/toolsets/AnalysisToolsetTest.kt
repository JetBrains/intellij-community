@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.AnalysisToolset
import com.intellij.mcpserver.util.relativizeIfPossible
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class AnalysisToolsetTest : McpToolsetTestBase() {
  @Test
  fun get_file_problems() = runBlocking {
    // No need to open file in editor - MainPassesRunner runs daemon analysis on any file
    testMcpTool(
      AnalysisToolset::get_file_problems.name,
      buildJsonObject {
        put("filePath", JsonPrimitive(project.baseDir.toNioPath().relativizeIfPossible(mainJavaFile)))
      },
      /*language=JSON*/ """{"filePath":"src/Main.java","errors":[]}"""
    )
  }

  // tool is disabled now
  //@Test
  fun build_project() = runBlocking {
    testMcpTool(
      AnalysisToolset::build_project.name,
      buildJsonObject {},
      /*language=JSON*/ """{"isSuccess":true,"problems":[]}"""
    )
  }

  @Test
  fun get_project_modules() = runBlocking {
    testMcpTool(
      AnalysisToolset::get_project_modules.name,
      buildJsonObject {},
      /*language=JSON*/ """{"modules":[{"name":"testModule","type":""}]}"""
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
