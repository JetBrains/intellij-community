@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.ReadToolset
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReadToolsetTest : McpToolsetTestBase() {
  private val readFileFixture = sourceRootFixture.virtualFileFixture(
    "read_file_sample.txt",
    """
      // Header A
      // Header B
      fun foo() {
        val x = 1
        if (x > 0) {
          println(x)
        }
      }
      fun bar() {
        println("bar")
      }
    """.trimIndent()
  )
  private val readFile by readFileFixture

  @Test
  fun read_file_returns_numbered_lines_from_offset() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("offset", JsonPrimitive(3))
        put("limit", JsonPrimitive(2))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains("L3: fun foo() {", "L4:   val x = 1")
      assertThat(text).doesNotContain("L5:   if (x > 0) {")
    }
  }

  @Test
  fun read_file_full_file_returns_all_lines() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains(
        "L1: // Header A",
        "L2: // Header B",
        "L3: fun foo() {",
        "L11: }"
      )
    }
  }

  @Test
  fun read_file_rejects_non_positive_offset() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("offset", JsonPrimitive(0))
      }
    ) { actualResult ->
      assertThat(actualResult.isError).isTrue()
    }
  }

  @Test
  fun read_file_rejects_missing_file() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive("does/not/exist.txt"))
      }
    ) { actualResult ->
      assertThat(actualResult.isError).isTrue()
    }
  }
}
