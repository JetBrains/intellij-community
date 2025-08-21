@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.Constants.MAX_USAGE_TEXT_CHARS
import com.intellij.mcpserver.toolsets.general.TextToolset
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class TextToolsetTest : McpToolsetTestBase() {
  private val longContentFixture = sourceRootFixture.virtualFileFixture("long_content.txt", "x".repeat(1001) + "SEARCH_TARGET" + "y".repeat(1001))

  @Test
  fun get_file_text_by_path() = runBlocking {
    testMcpTool(
      TextToolset::get_file_text_by_path.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(testJavaFile.name))
      },
      "Test.java content"
    )
  }

  @Test
  fun replace_file_text_by_path() = runBlocking {
    testMcpTool(
      TextToolset::replace_text_in_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(mainJavaFile.name))
        put("text", JsonPrimitive("updated content"))
      },
      "ok"
    )
  }

  @Test
  fun search_in_files_by_text_truncates_long_lines() = runBlocking {
    testMcpTool(
      TextToolset::search_in_files_by_text.name,
      buildJsonObject {
        put("searchText", JsonPrimitive("SEARCH_TARGET"))
      }
    ) { actualResult ->
      assertTrue { actualResult.textContent.text?.contains("x".repeat(MAX_USAGE_TEXT_CHARS) + "||SEARCH_TARGET||" + "y".repeat(MAX_USAGE_TEXT_CHARS)) ?: false }
    }
  }

  @Test
  fun replace_text_in_file_with_empty_old_text_should_not_hang() = runBlocking {
    // This test should fail with the current implementation due to endless loop
    // when oldText is empty
    testMcpTool(
      TextToolset::replace_text_in_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(testJavaFile.canonicalPath))
        put("oldText", JsonPrimitive(""))  // Empty string causes endless loop
        put("newText", JsonPrimitive("prefix"))
        put("replaceAll", JsonPrimitive(true))
      }
    ) { actualResult ->
      // Should fail with an error instead of hanging
      assertTrue { actualResult.isError == true && actualResult.textContent.text?.contains("empty") ?: false }
    }
  }
}