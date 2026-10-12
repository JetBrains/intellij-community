@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.mcpserver.toolsets.general.UniversalToolset
import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UniversalToolsetTest : GeneralMcpToolsetTestBase() {
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

  /**
   * A routed call is three rows: the `execute_tool` entry, the tool it dispatched to, and the dispatch itself. They
   * only describe one call if they agree on `tool_call_id` — otherwise the dispatch row cannot be attributed to the
   * tool that served it.
   */
  @Test
  fun routed_call_reports_one_tool_call_id_on_all_of_its_rows(@TestDisposable disposable: Disposable) {
    val rows = FUCollectorTestCase.collectLogEvents(disposable) {
      runBlocking(Dispatchers.Default) {
        testMcpTool(
          UniversalToolset::execute_tool.name,
          buildJsonObject {
            put("command", JsonPrimitive("reformat_file --files '[\"src/Main.java\"]'"))
          },
          "ok",
        )
      }
    }.filter { it.group.id == "mcpserver.events" }
      .filter { it.event.id == "mcp.tool.call" || it.event.id == "mcp.execute_tool.dispatch" }

    assertThat(rows.map { "${it.event.id}/${it.event.data["invocation_mode"] ?: "-"}" }).contains(
      "mcp.tool.call/ROUTER_ENTRY",
      "mcp.tool.call/VIA_ROUTER",
      "mcp.execute_tool.dispatch/-",
    )
    // "undefined" is what an absent value reports as, so it is asserted against rather than treated as one id.
    assertThat(rows.map { it.event.data["tool_call_id"] }.distinct())
      .hasSize(1)
      .doesNotContain(null, "undefined")
  }

  /**
   * A client the IDE did not launch has no chat to name. The fields must then be absent rather than blank: a
   * present-but-empty value would count that call into whatever bucket the empty string lands in.
   */
  @Test
  fun a_call_with_no_owner_reports_no_chat_and_no_turn(@TestDisposable disposable: Disposable) {
    val rows = FUCollectorTestCase.collectLogEvents(disposable) {
      runBlocking(Dispatchers.Default) {
        testMcpTool(
          UniversalToolset::execute_tool.name,
          buildJsonObject {
            put("command", JsonPrimitive("reformat_file --files '[\"src/Main.java\"]'"))
          },
          "ok",
        )
      }
    }.filter { it.group.id == "mcpserver.events" && it.event.id == "mcp.tool.call" }

    assertThat(rows).isNotEmpty()
    // The test session sets no owner key, which is the same state an external client is in.
    assertThat(rows.map { it.event.data["launch_origin"] }.distinct()).containsExactly("EXTERNAL_CLIENT")
    // "undefined" is what an absent value reports as; the point is that neither key is present at all.
    assertThat(rows.flatMap { listOf(it.event.data["agent_session_id"], it.event.data["turn_id"]) })
      .allMatch { it == null }
  }

  @Test
  fun execute_tool_reformat_file(): Unit = runBlocking(Dispatchers.Default) {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("reformat_file --files '[\"src/Main.java\"]'"))
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
  fun execute_tool_search_file_with_paths_array(): Unit = runBlocking(Dispatchers.Default) {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("search_file --q **/*.java --paths '[\"src/Test.java\"]'"))
      }
    ) { result ->
      assert(result.isError == false) { "search_file with paths array should succeed" }
      assertThat(parseSearchResult(result.textContent.text).filePaths()).containsExactly("src/Test.java")
    }
  }

  @Test
  fun parse_args_to_json_with_array_and_object_parameters() {
    val jsonArgs = parseUniversalArgs(
      listOf("--items", "[\"first\",\"second\"]", "--payload", "{\"enabled\":true}"),
      buildJsonObject {
        put("items", buildJsonObject { put("type", JsonPrimitive("array")) })
        put("payload", buildJsonObject { put("type", JsonPrimitive("object")) })
      },
    )

    assertThat(jsonArgs["items"]).isInstanceOf(JsonArray::class.java)
    assertThat(jsonArgs["items"].toString()).isEqualTo("[\"first\",\"second\"]")
    assertThat(jsonArgs["payload"]).isInstanceOf(JsonObject::class.java)
    assertThat(jsonArgs["payload"].toString()).isEqualTo("{\"enabled\":true}")
  }

  @Test
  fun parse_args_to_json_accepts_array_for_object_schema() {
    val jsonArgs = parseUniversalArgs(
      listOf("--payload", "[]"),
      buildJsonObject {
        put("payload", buildJsonObject { put("type", JsonPrimitive("object")) })
      },
    )

    assertThat(jsonArgs["payload"]).isInstanceOf(JsonArray::class.java)
    assertThat(jsonArgs["payload"].toString()).isEqualTo("[]")
  }

  @Test
  fun parse_args_to_json_rejects_malformed_array_parameter() {
    val error = assertThrows(McpExpectedError::class.java) {
      parseUniversalArgs(
        listOf("--items", "not-json"),
        buildJsonObject {
          put("items", buildJsonObject { put("type", JsonPrimitive("array")) })
        },
      )
    }

    assertThat(error.mcpErrorText).contains("Parameter 'items' expects a JSON array")
  }

  @Test
  fun parse_args_to_json_rejects_wrong_structured_json_shape() {
    val error = assertThrows(McpExpectedError::class.java) {
      parseUniversalArgs(
        listOf("--items", "{\"enabled\":true}"),
        buildJsonObject {
          put("items", buildJsonObject { put("type", JsonPrimitive("array")) })
        },
      )
    }

    assertThat(error.mcpErrorText).contains("Parameter 'items' expects a JSON array")
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
  fun execute_tool_rejects_an_unknown_parameter(): Unit = runBlocking(Dispatchers.Default) {
    testMcpTool(
      UniversalToolset::execute_tool.name,
      buildJsonObject {
        put("command", JsonPrimitive("search_file --q Test.java --line_end 240"))
      }
    ) { result ->
      assert(result.isError == true) { "An unknown parameter should be refused, not ignored" }
      val text = result.textContent.text
      assert(text.contains("line_end")) { "The error should name the refused flag, got: $text" }
    }
  }

  @Test
  fun resolve_flag_name_folds_case_and_underscores() {
    val schema = buildJsonObject {
      put("file_path", buildJsonObject { put("type", JsonPrimitive("string")) })
      put("offset", buildJsonObject { put("type", JsonPrimitive("integer")) })
    }
    val toolset = UniversalToolset()

    assertThat(toolset.resolveFlagName("file_path", schema)).isEqualTo("file_path")
    assertThat(toolset.resolveFlagName("filePath", schema)).isEqualTo("file_path")
    assertThat(toolset.resolveFlagName("filepath", schema)).isEqualTo("file_path")
    // parseArgsToJson converts a last line into a limit.
    assertThat(toolset.resolveFlagName("line_end", schema)).isNull()
    assertThat(toolset.resolveFlagName("nonsense", schema)).isNull()
  }

  @Test
  fun resolve_flag_name_reaches_camel_case_properties() {
    val schema = buildJsonObject {
      put("filePath", buildJsonObject { put("type", JsonPrimitive("string")) })
      put("errorsOnly", buildJsonObject { put("type", JsonPrimitive("boolean")) })
    }
    val toolset = UniversalToolset()

    assertThat(toolset.resolveFlagName("file_path", schema)).isEqualTo("filePath")
    assertThat(toolset.resolveFlagName("errors_only", schema)).isEqualTo("errorsOnly")
  }

  @Test
  fun parse_args_to_json_maps_a_folded_name_onto_its_schema_property() {
    val jsonArgs = parseUniversalArgs(
      listOf("--filePath", "src/Main.java", "--Offset", "10"),
      buildJsonObject {
        put("file_path", buildJsonObject { put("type", JsonPrimitive("string")) })
        put("offset", buildJsonObject { put("type", JsonPrimitive("integer")) })
      },
    )

    assertThat(jsonArgs["file_path"].toString()).isEqualTo("\"src/Main.java\"")
    // Resolved before conversion, so the integer schema of the real property applies.
    assertThat(jsonArgs["offset"]).isEqualTo(JsonPrimitive(10L))
  }

  @Test
  fun parse_args_to_json_converts_a_last_line_into_a_limit() {
    val schema = buildJsonObject {
      put("file_path", buildJsonObject { put("type", JsonPrimitive("string")) })
      put("offset", buildJsonObject { put("type", JsonPrimitive("integer")) })
      put("limit", buildJsonObject { put("type", JsonPrimitive("integer")) })
    }

    val windowed = parseUniversalArgs(
      listOf("--file_path", "src/Main.java", "--offset", "10", "--end_line", "29"),
      schema,
    )
    assertThat(windowed["file_path"].toString()).isEqualTo("\"src/Main.java\"")
    assertThat(windowed["offset"]).isEqualTo(JsonPrimitive(10L))
    // Lines 10..29 inclusive is 20 lines, not 29.
    assertThat(windowed["limit"]).isEqualTo(JsonPrimitive(20L))

    // The hyphenated spelling folds to the same name, and a missing offset means the file's start.
    val fromStart = parseUniversalArgs(
      listOf("--file_path", "src/Main.java", "--end-line", "90"),
      schema,
    )
    assertThat(fromStart["limit"]).isEqualTo(JsonPrimitive(90L))
    assertThat(fromStart["offset"]).isNull()
  }

  @Test
  fun parse_args_to_json_refuses_both_a_limit_and_a_last_line() {
    val error = assertThrows(McpExpectedError::class.java) {
      parseUniversalArgs(
        listOf("--file_path", "src/Main.java", "--limit", "5", "--line_end", "999"),
        buildJsonObject {
          put("file_path", buildJsonObject { put("type", JsonPrimitive("string")) })
          put("offset", buildJsonObject { put("type", JsonPrimitive("integer")) })
          put("limit", buildJsonObject { put("type", JsonPrimitive("integer")) })
        },
      )
    }

    assertThat(error.mcpErrorText).contains("--limit")
    assertThat(error.mcpErrorText).contains("--line_end")
  }

  @Test
  fun parse_args_to_json_refuses_a_last_line_before_the_offset() {
    val error = assertThrows(McpExpectedError::class.java) {
      parseUniversalArgs(
        listOf("--file_path", "src/Main.java", "--offset", "100", "--end_line", "20"),
        buildJsonObject {
          put("file_path", buildJsonObject { put("type", JsonPrimitive("string")) })
          put("offset", buildJsonObject { put("type", JsonPrimitive("integer")) })
          put("limit", buildJsonObject { put("type", JsonPrimitive("integer")) })
        },
      )
    }

    assertThat(error.mcpErrorText).contains("--end_line")
    assertThat(error.mcpErrorText).contains("100")
  }

  @Test
  fun parse_args_to_json_only_converts_a_last_line_for_a_read_window() {
    // The `search_file` limit counts hits, not lines.
    val error = assertThrows(McpExpectedError::class.java) {
      parseUniversalArgs(
        listOf("--q", "Test.java", "--end_line", "240"),
        buildJsonObject {
          put("q", buildJsonObject { put("type", JsonPrimitive("string")) })
          put("limit", buildJsonObject { put("type", JsonPrimitive("integer")) })
        },
      )
    }

    assertThat(error.mcpErrorText).contains("Unknown parameter '--end_line'")
  }

  @Test
  fun parse_args_to_json_rejects_an_unknown_parameter() {
    val error = assertThrows(McpExpectedError::class.java) {
      parseUniversalArgs(
        listOf("--recursive", "true"),
        buildJsonObject {
          put("file_path", buildJsonObject { put("type", JsonPrimitive("string")) })
          put("limit", buildJsonObject { put("type", JsonPrimitive("integer")) })
        },
      )
    }

    assertThat(error.mcpErrorText).contains("Unknown parameter '--recursive'")
    assertThat(error.mcpErrorText).contains("--file_path")
  }

  @Test
  fun parse_args_to_json_passes_everything_through_an_empty_schema() {
    val jsonArgs = parseUniversalArgs(listOf("--anything", "value"), buildJsonObject { })

    assertThat(jsonArgs["anything"].toString()).isEqualTo("\"value\"")
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
      assert(textContent.text.contains("Missing required parameters") || textContent.text.contains("files")) {
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

  private fun parseUniversalArgs(args: List<String>, propertiesSchema: JsonObject): JsonObject {
    return UniversalToolset().parseArgsToJson(args, propertiesSchema)
  }
}
