@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.TextToolset
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TextToolsetTest : GeneralMcpToolsetTestBase() {
  private val emptyFileFixture = sourceRootFixture.virtualFileFixture("empty.txt", "")
  private val emptyFile by emptyFileFixture

  @Test
  fun replace_file_text_by_path() = runBlocking(Dispatchers.Default) {
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
  fun replace_text_in_file_with_empty_old_text_on_non_empty_file_should_fail() = runBlocking(Dispatchers.Default) {
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
      assertThat(actualResult.isError).isTrue()
      assertThat(actualResult.textContent.text).contains("empty")
    }
  }

  @Test
  fun replace_text_in_file_with_empty_old_text_on_empty_file_should_succeed() = runBlocking(Dispatchers.Default) {
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
