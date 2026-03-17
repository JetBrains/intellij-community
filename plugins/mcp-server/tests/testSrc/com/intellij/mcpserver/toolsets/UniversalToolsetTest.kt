@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.UniversalToolset
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class UniversalToolsetTest : McpToolsetTestBase() {

  @Test
  fun execute_tool_reformat_file() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("reformat_file --path src/Main.java"))
      },
      "ok"
    )
  }

  @Test
  fun execute_tool_find_files_by_name() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("find_files_by_name_keyword --nameKeyword test"))
      }
    ) { result ->
      val textContent = result.textContent
      assert(textContent.text.contains("Test.java")) { "Result should contain Test.java" }
    }
  }

  @Test
  fun execute_tool_find_files_by_glob() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("find_files_by_glob --globPattern **/*.java"))
      }
    ) { result ->
      val textContent = result.textContent
      assert(textContent.text.contains("Main.java")) { "Result should contain Main.java" }
      assert(textContent.text.contains("Test.java")) { "Result should contain Test.java" }
      assert(textContent.text.contains("Class.java")) { "Result should contain Class.java" }
    }
  }

  @Test
  fun execute_tool_create_file() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("create_new_file --pathInProject src/NewFile.java"))
      },
      "[success]"
    )
  }

  @Test
  fun execute_tool_with_boolean_parameter() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("create_new_file --pathInProject src/AnotherFile.java --overwrite true"))
      },
      "[success]"
    )
  }

  @Test
  fun execute_tool_with_integer_parameter() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("find_files_by_glob --globPattern **/*.java --fileCountLimit 5"))
      }
    ) { result ->
      val textContent = result.textContent
      assert(textContent.text.contains("files")) { "Result should contain files" }
    }
  }

  @Test
  fun execute_tool_nonexistent_tool() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("nonexistent_tool --param value"))
      }
    ) { result ->
      assert(result.isError == true) { "Should return an error for nonexistent tool" }
      val textContent = result.textContent
      assert(textContent.text.contains("not found")) { "Error message should mention tool not found" }
    }
  }

  @Test
  fun execute_tool_missing_required_parameter() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("reformat_file"))
      }
    ) { result ->
      assert(result.isError == true) { "Should return an error for missing required parameter" }
      val textContent = result.textContent
      assert(textContent.text.contains("Missing required parameters") || textContent.text.contains("path")) {
        "Error message should mention missing required parameter"
      }
    }
  }

  @Test
  fun execute_tool_invalid_argument_format() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("reformat_file invalidarg"))
      }
    ) { result ->
      assert(result.isError == true) { "Should return an error for invalid argument format" }
      val textContent = result.textContent
      assert(textContent.text.contains("Invalid argument format") || textContent.text.contains("Expected '--paramName value'")) {
        "Error message should mention invalid argument format"
      }
    }
  }

  @Test
  fun execute_tool_empty_command() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive(""))
      }
    ) { result ->
      assert(result.isError == true) { "Should return an error for empty command" }
      val textContent = result.textContent
      assert(textContent.text.contains("Command is empty")) {
        "Error message should mention empty command"
      }
    }
  }

  @Test
  fun execute_tool_help_all_tools() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("--help"))
      }
    ) { result ->
      val textContent = result.textContent
      assert(textContent.text.contains("Available MCP Tools")) {
        "Help should show 'Available MCP Tools'"
      }
      assert(textContent.text.contains("reformat_file")) {
        "Help should list reformat_file tool"
      }
      assert(textContent.text.contains("find_files_by_glob")) {
        "Help should list find_files_by_glob tool"
      }
    }
  }

  @Test
  fun execute_tool_help_specific_tool() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("reformat_file --help"))
      }
    ) { result ->
      val textContent = result.textContent
      assert(textContent.text.contains("Tool: reformat_file")) {
        "Help should show tool name"
      }
      assert(textContent.text.contains("Description:")) {
        "Help should show description section"
      }
      assert(textContent.text.contains("Parameters:")) {
        "Help should show parameters section"
      }
      assert(textContent.text.contains("--path")) {
        "Help should show path parameter"
      }
      assert(textContent.text.contains("[required]") || textContent.text.contains("[optional]")) {
        "Help should indicate if parameter is required or optional"
      }
      assert(textContent.text.contains("Example:")) {
        "Help should show example"
      }
    }
  }

  @Test
  fun execute_tool_help_nonexistent_tool() = runBlocking {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("nonexistent_tool --help"))
      }
    ) { result ->
      assert(result.isError == true) { "Should return an error for nonexistent tool" }
      val textContent = result.textContent
      assert(textContent.text.contains("not found")) {
        "Error message should mention tool not found"
      }
      assert(textContent.text.contains("--help")) {
        "Error message should suggest using --help"
      }
    }
  }
}
