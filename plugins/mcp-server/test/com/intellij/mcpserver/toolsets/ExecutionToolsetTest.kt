@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.ExecutionToolset
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class ExecutionToolsetTest : McpToolsetTestBase() {
  @Test
  fun get_run_configurations() = runBlocking {
    testMcpTool(
      ExecutionToolset::get_run_configurations.name,
      buildJsonObject {},
      """{"configurations":[]}"""
    )
  }

  @Test
  fun execute_run_configuration() = runBlocking {
    testMcpTool(
      ExecutionToolset::execute_run_configuration.name,
      buildJsonObject {
        put("configName", JsonPrimitive("test-config"))
      },
      "Run configuration with name 'test-config' not found."
    )
  }
}