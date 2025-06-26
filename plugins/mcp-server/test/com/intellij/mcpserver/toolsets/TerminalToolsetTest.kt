@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.terminal.TerminalToolset
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class TerminalToolsetTest : McpToolsetTestBase() {
  @Test
  fun get_terminal_text() = runBlocking {
    testMcpTool(
      TerminalToolset::get_terminal_text.name,
      buildJsonObject {},
      "No terminal available"
    )
  }

  @Test
  fun execute_terminal_command() = runBlocking {
    testMcpTool(
      TerminalToolset::execute_terminal_command.name,
      buildJsonObject {
        put("command", JsonPrimitive("echo 'Hello, World!'"))
      },
      "No terminal available"
    )
  }
}