@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.vcs.VcsToolset
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class VcsToolsetTest : McpToolsetTestBase() {
  @Disabled("Configure VCS in test project")
  @Test
  fun get_project_vcs_status() = runBlocking {
    testMcpTool(
      VcsToolset::get_project_vcs_status.name,
      buildJsonObject {},
      "[]"
    )
  }

  @Disabled("Configure VCS in test project")
  @Test
  fun find_commit_by_message() = runBlocking {
    testMcpTool(
      VcsToolset::find_commit_by_message.name,
      buildJsonObject {
        put("text", JsonPrimitive("test commit"))
      },
      "Error: No VCS configured for this project"
    )
  }
}