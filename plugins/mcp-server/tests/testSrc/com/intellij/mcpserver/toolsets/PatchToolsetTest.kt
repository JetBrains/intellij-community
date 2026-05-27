@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.PatchToolset
import com.intellij.mcpserver.toolsets.general.ReadToolset
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PatchToolsetTest : McpToolsetTestBase() {
  // Touch the class-level fixture so the file exists before the delete test runs.
  private val classJavaFile by classJavaFileFixture

  @Test
  fun apply_patch_adds_file() = runBlocking {
    val pathInProject = "src/notes.txt"
    val patch = listOf(
      "*** Begin Patch",
      "*** Add File: $pathInProject",
      "+alpha",
      "+beta",
      "*** End Patch",
    ).joinToString("\n", postfix = "\n")

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file.",
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(pathInProject))
      },
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains("L1: alpha", "L2: beta")
    }
  }

  @Test
  fun apply_patch_updates_file() = runBlocking {
    // testJavaFile is created by the base with content "Test.java content".
    val pathInProject = "src/${testJavaFile.name}"
    val patch = listOf(
      "*** Begin Patch",
      "*** Update File: $pathInProject",
      "@@",
      "-Test.java content",
      "+updated content",
      "*** End Patch",
    ).joinToString("\n", postfix = "\n")

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file.",
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(pathInProject))
      },
    ) { actualResult ->
      val text = actualResult.textContent.text
      assertThat(text).contains("updated content")
      assertThat(text).doesNotContain("Test.java content")
    }
  }

  @Test
  fun apply_patch_deletes_file() = runBlocking {
    val pathInProject = "src/${classJavaFile.name}"
    val patch = listOf(
      "*** Begin Patch",
      "*** Delete File: $pathInProject",
      "*** End Patch",
    ).joinToString("\n", postfix = "\n")

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file.",
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(pathInProject))
      },
    ) { actualResult ->
      assertThat(actualResult.isError).isTrue()
    }
  }
}
