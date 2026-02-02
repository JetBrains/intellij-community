@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.Constants.MAX_USAGE_TEXT_CHARS
import com.intellij.mcpserver.toolsets.general.TextToolset
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.test.assertTrue

class TextToolsetTest : McpToolsetTestBase() {
  private val longContentFixture = sourceRootFixture.virtualFileFixture("long_content.txt", "x".repeat(1001) + "SEARCH_TARGET" + "y".repeat(1001))
  private val subdir1 = moduleFixture.sourceRootFixture(pathFixture = projectFixture.pathInProjectFixture(Path("subdir1"))).virtualFileFixture("file_in_subdir.txt", "FINDME_IN_SUBDIR")
  private val subdir2 = moduleFixture.sourceRootFixture(pathFixture = projectFixture.pathInProjectFixture(Path("subdir2"))).virtualFileFixture("file_in_subdir.txt", "FINDME_IN_SUBDIR")

  private val emptyFileFixture = sourceRootFixture.virtualFileFixture("empty.txt", "")
  private val emptyFile by emptyFileFixture

  @Test
  fun get_file_text_by_path() = runBlocking {
    testMcpTool(
      TextToolset::get_file_text_by_path.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(project.baseDir.toNioPath().relativizeIfPossible(testJavaFile)))
      },
      "Test.java content"
    )
  }

  @Test
  fun replace_file_text_by_path() = runBlocking {
    testMcpTool(
      TextToolset::replace_text_in_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(project.baseDir.toNioPath().relativizeIfPossible(mainJavaFile)))
        put("oldText", JsonPrimitive("Main.java content"))
        put("newText", JsonPrimitive("updated content"))
      },
      "[success]"
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
  fun search_in_files_by_text_respects_directory_scope() = runBlocking {
    testMcpTool(
      TextToolset::search_in_files_by_text.name,
      buildJsonObject {
        put("searchText", JsonPrimitive("FINDME_IN_SUBDIR"))
        put("directoryToSearch", JsonPrimitive("src/subdir1"))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertTrue { text.contains("subdir1") && !text.contains("subdir2") }
    }
  }

  @Test
  fun search_in_files_by_text_return_result_for_all_subdirs() = runBlocking {
    testMcpTool(
      TextToolset::search_in_files_by_text.name,
      buildJsonObject {
        put("searchText", JsonPrimitive("FINDME_IN_SUBDIR"))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertTrue { text.contains("subdir1") && text.contains("subdir2") }
    }
  }

  @Test
  fun search_in_files_by_regexp_respects_directory_scope() = runBlocking {
    testMcpTool(
      TextToolset::search_in_files_by_regex.name,
      buildJsonObject {
        put("regexPattern", JsonPrimitive("FINDME_IN_SUBDIR"))
        put("directoryToSearch", JsonPrimitive("src/subdir1"))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertTrue { text.contains("subdir1") && !text.contains("subdir2") }
    }
  }

  @Test
  fun search_in_files_by_regexp_return_result_for_all_subdirs() = runBlocking {
    testMcpTool(
      TextToolset::search_in_files_by_regex.name,
      buildJsonObject {
        put("regexPattern", JsonPrimitive("FINDME_IN_SUBDIR"))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertTrue { text.contains("subdir1") && text.contains("subdir2") }
    }
  }

  @Test
  fun replace_text_in_file_with_empty_old_text_on_non_empty_file_should_fail() = runBlocking {
    // Should fail with an error when oldText is empty but file has content
    testMcpTool(
      TextToolset::replace_text_in_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(project.baseDir.toNioPath().relativizeIfPossible(testJavaFile)))
        put("oldText", JsonPrimitive(""))  // Empty string on non-empty file
        put("newText", JsonPrimitive("prefix"))
        put("replaceAll", JsonPrimitive(true))
      }
    ) { actualResult ->
      // Should fail with an error about empty oldText on non-empty file
      assertTrue { actualResult.isError == true && actualResult.textContent.text?.contains("empty") ?: false }
    }
  }

  @Test
  fun replace_text_in_file_with_empty_old_text_on_empty_file_should_succeed() = runBlocking {
    // Should succeed when oldText is empty and file is empty (LLM create-then-fill workflow)
    testMcpTool(
      TextToolset::replace_text_in_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(project.baseDir.toNioPath().relativizeIfPossible(emptyFile)))
        put("oldText", JsonPrimitive(""))  // Empty string on empty file
        put("newText", JsonPrimitive("new content"))
        put("replaceAll", JsonPrimitive(true))
      },
      "[success]"
    )
  }
}