@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.debugger.DebuggerToolset
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class DebuggerToolsetTest : McpToolsetTestBase() {
  @Test
  fun get_debugger_breakpoints() = runBlocking {
    testMcpTool(
      DebuggerToolset::get_debugger_breakpoints.name,
      buildJsonObject {},
      "[]"
    )
  }


  @Disabled("Tool doesn't work")
  @Test
  fun toggle_debugger_breakpoint() = runBlocking {
    testMcpTool(
      DebuggerToolset::toggle_debugger_breakpoint.name,
      buildJsonObject {
        put("filePathInProject", JsonPrimitive("Main.java"))
        put("line", JsonPrimitive(0))
      },
      "[]"
    )
  }
}