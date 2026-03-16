@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.vcs.VcsToolset
import io.kotest.common.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class VcsToolsetTest : McpToolsetTestBase() {
  @Disabled("Configure VCS in test project")
  @Test
  fun get_project_vcs_status() = runBlocking {
    testMcpTool(
      VcsToolset::get_repositories.name,
      buildJsonObject {},
      "[]"
    )
  }
}