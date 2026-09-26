@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.IdeScriptToolset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IdeScriptToolsetTest : GeneralMcpToolsetTestBase() {
  @Test
  fun run_ide_script_returns_the_last_expression() = runBlocking(Dispatchers.Default) {
    runScript("1 + 1") { result ->
      assertThat(result).contains("\"success\":true").contains("\"result\":\"2\"")
    }
  }

  @Test
  fun run_ide_script_binds_the_project() = runBlocking(Dispatchers.Default) {
    runScript("project.name") { result ->
      assertThat(result).contains("\"success\":true").contains(project.name)
    }
  }

  private suspend fun runScript(script: String, check: (String) -> Unit) {
    testMcpTool(
      IdeScriptToolset::run_ide_script.name,
      buildJsonObject { put("script", JsonPrimitive(script)) },
    ) { result -> check(result.textContent.text) }
  }
}
