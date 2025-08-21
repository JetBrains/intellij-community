@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.ProjectToolset
import io.kotest.common.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class ProjectToolsetTest : McpToolsetTestBase() {
  
  @Test
  fun list_projects() = runBlocking {
    testMcpTool(
      ProjectToolset::list_projects.name,
      buildJsonObject { },
    ) { result ->
      val textContent = result.textContent
      // The result should contain project information
      assert(textContent.text.contains("projects")) { "Result should contain projects array" }
      assert(textContent.text.contains("name")) { "Result should contain project name" }
      assert(textContent.text.contains("basePath")) { "Result should contain project basePath" }
    }
  }
}
