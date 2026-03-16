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
  fun read_file_slice_returns_numbered_lines() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("slice"))
        put("start_line", JsonPrimitive(3))
        put("max_lines", JsonPrimitive(2))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains("L3: fun foo() {", "L4:   val x = 1")
    }
  }

  @Test
  fun read_file_slice_rejects_offset_beyond_file_length() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("slice"))
        put("start_line", JsonPrimitive(999))
        put("max_lines", JsonPrimitive(1))
      }
    ) { actualResult ->
      assertThat(actualResult.isError).isTrue()
      assertThat(actualResult.textContent.text).contains("start_line exceeds file length")
    }
  }

  @Test
  fun read_file_slice_rejects_non_positive_limit() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("slice"))
        put("start_line", JsonPrimitive(1))
        put("max_lines", JsonPrimitive(0))
      }
    ) { actualResult ->
      assertThat(actualResult.isError).isTrue()
      assertThat(actualResult.textContent.text).contains("max_lines must be > 0")
    }
  }

  @Test
  fun read_file_range_by_lines_is_inclusive() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("lines"))
        put("start_line", JsonPrimitive(2))
        put("end_line", JsonPrimitive(4))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains(
        "L2: // Header B",
        "L3: fun foo() {",
        "L4:   val x = 1",
      )
      assertThat(text).doesNotContain("L5:   if (x > 0) {")
    }
  }

  @Test
  fun read_file_indentation_includes_header_lines() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("indentation"))
        put("max_lines", JsonPrimitive(3))
        put("start_line", JsonPrimitive(3))
        put("include_header", JsonPrimitive(true))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains(
        "L1: // Header A",
        "L2: // Header B",
        "L3: fun foo() {",
      )
    }
  }

  @Test
  fun read_file_indentation_respects_max_lines() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("indentation"))
        put("start_line", JsonPrimitive(3))
        put("max_lines", JsonPrimitive(1))
      }
    ) { actualResult ->
      assertThat(actualResult.textContent.text).isEqualTo("L3: fun foo() {")
    }
  }

  @Test
  fun read_file_indentation_excludes_siblings_when_disabled() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("indentation"))
        put("max_lines", JsonPrimitive(20))
        put("start_line", JsonPrimitive(3))
        put("include_siblings", JsonPrimitive(false))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains("L3: fun foo() {")
      assertThat(text).doesNotContain("fun bar() {")
    }
  }

  @Test
  fun read_file_range_by_line_column_includes_context_lines() = runBlocking {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("line_columns"))
        put("start_line", JsonPrimitive(4))
        put("start_column", JsonPrimitive(3))
        put("end_line", JsonPrimitive(4))
        put("end_column", JsonPrimitive(6))
        put("context_lines", JsonPrimitive(1))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains(
        "L3: fun foo() {",
        "L4:   val x = 1",
        "L5:   if (x > 0) {",
      )
    }
  }

  @Test
  fun read_file_range_by_offsets_reads_exact_line() = runBlocking {
    val fileText = readFile.contentsToByteArray().toString(Charsets.UTF_8)
    val needle = "println(\"bar\")"
    val startOffset = fileText.indexOf(needle)
    val endOffset = startOffset + needle.length
    assertThat(startOffset).isGreaterThanOrEqualTo(0)

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(project.projectDirectory.relativizeIfPossible(readFile)))
        put("mode", JsonPrimitive("offsets"))
        put("start_offset", JsonPrimitive(startOffset))
        put("end_offset", JsonPrimitive(endOffset))
      }
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains("L10:   println(\"bar\")")
    }
  }
}
