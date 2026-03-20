@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.TextToolset
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class TextToolsetTest : McpToolsetTestBase() {
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
  fun get_file_text_by_path_accepts_lowercase_enum() = runBlocking {
    testMcpTool(
      TextToolset::get_file_text_by_path.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(project.baseDir.toNioPath().relativizeIfPossible(testJavaFile)))
        put("truncateMode", JsonPrimitive("start"))
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
      assertTrue { actualResult.isError == true && actualResult.textContent.text.contains("empty") }
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
