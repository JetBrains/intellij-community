@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.AnalysisToolset
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class AnalysisToolsetTest : McpToolsetTestBase() {
  @Test
  fun get_file_problems() = runBlocking(Dispatchers.Default) {
    // This test requires Java plugin to detect syntax errors in Java files
    assumeTrue(isJavaPluginInstalled(), "Java plugin is required for this test")

    // No need to open file in editor - MainPassesRunner runs daemon analysis on any file
    // The test file contains invalid Java code "Main.java content", so errors should be detected
    testMcpTool(
      AnalysisToolset::get_file_problems.name,
      buildJsonObject {
        put("filePath", JsonPrimitive(project.baseDir.toNioPath().relativizeIfPossible(mainJavaFile)))
        put("errorsOnly", JsonPrimitive(false))
      },
    ) { result ->
      val text = result.textContent.text
      assertThat(text).contains(""""filePath":"src/Main.java"""")
      assertThat(text).contains(""""errors":[{"""")
    }
  }

  // tool is disabled now
  //@Test
  fun build_project() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      AnalysisToolset::build_project.name,
      buildJsonObject {},
      /*language=JSON*/ """{"isSuccess":true,"problems":[]}"""
    )
  }

  @Test
  fun get_project_modules() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      AnalysisToolset::get_project_modules.name,
      buildJsonObject {},
      /*language=JSON*/ """{"modules":[{"name":"testModule","type":""}]}"""
    )
  }

  @Test
  fun get_project_dependencies() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      AnalysisToolset::get_project_dependencies.name,
      buildJsonObject {},
      """{"dependencies":[]}"""
    )
  }

  // TODO handle it better
  /**
   * Checks if the Java plugin is installed.
   * Use this to skip tests that require Java language support.
   */
  private fun isJavaPluginInstalled(): Boolean {
    return PluginManagerCore.isPluginInstalled(PluginId.getId("com.intellij.java"))
  }
}
