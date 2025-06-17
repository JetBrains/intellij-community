@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.BuiltinGeneralToolset
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class BuiltinGeneralToolsetTest : McpToolsetTestBase() {
  @Disabled("Depends on the previous runs that may create files via fixture")
  @Test
  fun search_in_files_content() = runBlocking {
    testMcpTool(
      BuiltinGeneralToolset::search_in_files_content.name,
      buildJsonObject {
        put("searchText", JsonPrimitive("content"))
      },
      """[{"path": "Main.java", "name": "Main.java"},
{"path": "Class.java", "name": "Class.java"},
{"path": "Test.java", "name": "Test.java"}]"""
    )
  }

  @Test
  fun get_run_configurations() = runBlocking {
    testMcpTool(
      BuiltinGeneralToolset::get_run_configurations.name,
      buildJsonObject {},
      "[]"
    )
  }

  @Test
  fun run_configuration() = runBlocking {
    testMcpTool(
      BuiltinGeneralToolset::run_configuration.name,
      buildJsonObject {
        put("configName", JsonPrimitive("test-config"))
      },
      "Run configuration with name 'test-config' not found."
    )
  }

  @Test
  fun get_project_modules() = runBlocking {
    testMcpTool(
      BuiltinGeneralToolset::get_project_modules.name,
      buildJsonObject {},
      "[testModule]"
    )
  }

  @Test
  fun get_project_dependencies() = runBlocking {
    testMcpTool(
      BuiltinGeneralToolset::get_project_dependencies.name,
      buildJsonObject {},
      "[]"
    )
  }

  @Test
  fun list_available_actions() = runBlocking {
    testMcpTool(
      BuiltinGeneralToolset::list_available_actions.name,
      buildJsonObject {}
    ) {
      assert(it.textContent.text?.contains(""""id": "About"""") == true) { "'About' action not found"}
    }
  }

  @Test
  fun execute_action_by_id() = runBlocking {
    testMcpTool(
      BuiltinGeneralToolset::execute_action_by_id.name,
      buildJsonObject {
        put("actionId", JsonPrimitive("SaveAll"))
      },
      "ok"
    )
  }

  @Disabled("IMHO Useless tool. To remove")
  @Test
  fun get_progress_indicators() = runBlocking {
    testMcpTool(
      BuiltinGeneralToolset::get_progress_indicators.name,
      buildJsonObject {},
      "[]"
    )
  }

  @Test
  @Disabled("Imho useless tool. To remove")
  fun wait_tool() = runBlocking {
    testMcpTool(
      BuiltinGeneralToolset::wait.name,
      buildJsonObject {
        put("milliseconds", JsonPrimitive(100)) // Short wait for testing
      },
      "ok"
    )
  }
}
