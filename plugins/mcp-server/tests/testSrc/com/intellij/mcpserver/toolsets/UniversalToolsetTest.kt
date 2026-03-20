@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.mcpserver.toolsets.general.UniversalToolset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UniversalToolsetTest : McpToolsetTestBase() {
  private var oldInvocationMode = McpSessionInvocationMode.DIRECT
  private val json = Json { ignoreUnknownKeys = true }

  @Serializable
  private data class SearchItem(
    val filePath: String,
  )

  @Serializable
  private data class SearchResult(
    val items: List<SearchItem> = emptyList(),
    val more: Boolean = false,
  )

  private fun parseSearchResult(text: String?): SearchResult {
    val payload = text ?: error("Tool call result should include text content")
    return json.decodeFromString(SearchResult.serializer(), payload)
  }

  private fun SearchResult.filePaths(): List<String> = items.map { it.filePath }

  @BeforeEach
  fun setUpInvocationMode() {
    val settings = McpToolFilterSettings.getInstance()
    oldInvocationMode = settings.invocationMode
    settings.invocationMode = McpSessionInvocationMode.VIA_ROUTER
  }

  @AfterEach
  fun restoreInvocationMode() {
    McpToolFilterSettings.getInstance().invocationMode = oldInvocationMode
  }

  @Test
  fun execute_tool_reformat_file(): Unit = runBlocking(Dispatchers.Default) {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("reformat_file --path src/Main.java"))
      },
      "ok"
    )
  }

  @Test
  fun execute_tool_search_file_by_name(): Unit = runBlocking(Dispatchers.Default) {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("search_file --q Test.java"))
      }
    ) { result ->
      val searchResult = parseSearchResult(result.textContent.text)
      assert(searchResult.filePaths().contains("src/Test.java")) { "Result should contain src/Test.java" }
    }
  }

  @Test
  fun execute_tool_search_file_by_glob(): Unit = runBlocking(Dispatchers.Default) {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("search_file --q **/*.java"))
      }
    ) { result ->
      val filePaths = parseSearchResult(result.textContent.text).filePaths()
      assert(filePaths.contains("src/Main.java")) { "Result should contain src/Main.java" }
      assert(filePaths.contains("src/Test.java")) { "Result should contain src/Test.java" }
      assert(filePaths.contains("src/Class.java")) { "Result should contain src/Class.java" }
    }
  }

  @Test
  fun execute_tool_create_file(): Unit = runBlocking(Dispatchers.Default) {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("create_new_file --pathInProject src/NewFile.java"))
      },
      "[success]"
    )
  }

  @Test
  fun execute_tool_with_boolean_parameter(): Unit = runBlocking(Dispatchers.Default) {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("create_new_file --pathInProject src/AnotherFile.java --overwrite true"))
      },
      "[success]"
    )
  }

  @Test
  fun execute_tool_search_file_with_limit(): Unit = runBlocking(Dispatchers.Default) {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("search_file --q **/*.java --limit 1"))
      }
    ) { result ->
      assert(result.isError == false) { "search_file with limit should succeed" }
      val searchResult = parseSearchResult(result.textContent.text)
      assert(searchResult.items.size == 1) { "Result should contain exactly one item" }
      assert(searchResult.more) { "Result should indicate there are more matches" }
    }
  }

  @Test
  fun execute_tool_nonexistent_tool(): Unit = runBlocking(Dispatchers.Default) {
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
  fun execute_tool_missing_required_parameter(): Unit = runBlocking(Dispatchers.Default) {
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
  fun execute_tool_invalid_argument_format(): Unit = runBlocking(Dispatchers.Default) {
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
  fun execute_tool_empty_command(): Unit = runBlocking(Dispatchers.Default) {
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
  fun execute_tool_help_all_tools(): Unit = runBlocking(Dispatchers.Default) {
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
      assert(textContent.text.contains("search_file")) {
        "Help should list search_file tool"
      }
    }
  }

  @Test
  fun execute_tool_help_specific_tool(): Unit = runBlocking(Dispatchers.Default) {
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
  fun execute_tool_help_nonexistent_tool(): Unit = runBlocking(Dispatchers.Default) {
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
