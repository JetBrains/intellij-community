@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.FileToolset
import com.intellij.mcpserver.toolsets.general.PatchToolset
import com.intellij.mcpserver.toolsets.general.ReadToolset
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PatchToolsetTest : GeneralMcpToolsetTestBase() {
  @Test
  fun apply_patch_adds_file() = runBlocking(Dispatchers.Default) {
    val pathInProject = "src/notes.txt"
    val patch = buildPatch(
      "*** Begin Patch",
      "*** Add File: $pathInProject",
      "+alpha",
      "+beta",
      "*** End Patch",
    )

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file."
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(pathInProject))
      },
      renderNumberedText("alpha\nbeta\n")
    )
  }

  @Test
  fun apply_patch_updates_file() = runBlocking(Dispatchers.Default) {
    val pathInProject = "src/Test.java"
    val patch = buildPatch(
      "*** Begin Patch",
      "*** Update File: $pathInProject",
      "@@",
      "-Test.java content",
      "+updated content",
      "*** End Patch",
    )

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file."
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(pathInProject))
      },
      renderNumberedText("updated content")
    )
  }

  @Test
  fun apply_patch_handles_noop_update() = runBlocking(Dispatchers.Default) {
    val pathInProject = "src/Test.java"
    val patch = buildPatch(
      "*** Begin Patch",
      "*** Update File: $pathInProject",
      "@@",
      "-Test.java content",
      "+Test.java content",
      "*** End Patch",
    )

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file."
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(pathInProject))
      },
      renderNumberedText("Test.java content")
    )
  }

  @Test
  fun apply_patch_updates_from_unsaved_document_text() = runBlocking(Dispatchers.Default) {
    val pathInProject = "src/Test.java"
    readAndEdtWriteAction {
      val document = FileDocumentManager.getInstance().getDocument(testJavaFile)
                     ?: throw AssertionError("Could not get document for $pathInProject")
      writeAction {
        document.setText("unsaved content\n")
      }
    }

    val patch = buildPatch(
      "*** Begin Patch",
      "*** Update File: $pathInProject",
      "@@",
      "-unsaved content",
      "+updated from unsaved",
      "*** End Patch",
    )

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file."
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(pathInProject))
      },
      renderNumberedText("updated from unsaved\n")
    )
  }

  @Test
  fun apply_patch_deletes_file() = runBlocking(Dispatchers.Default) {
    val pathInProject = "src/delete-me.txt"
    testMcpTool(
      FileToolset::create_new_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(pathInProject))
        put("text", JsonPrimitive("delete me\n"))
      },
      "[success]"
    )

    val patch = buildPatch(
      "*** Begin Patch",
      "*** Delete File: $pathInProject",
      "*** End Patch",
    )

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file."
    )

    assertReadFails(pathInProject)
  }

  @Test
  fun apply_patch_moves_and_updates_file() = runBlocking(Dispatchers.Default) {
    val oldPath = "src/old.txt"
    val newPath = "src/moved/new.txt"

    testMcpTool(
      FileToolset::create_new_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(oldPath))
        put("text", JsonPrimitive("alpha\nbeta\n"))
      },
      "[success]"
    )

    val patch = buildPatch(
      "*** Begin Patch",
      "*** Update File: $oldPath",
      "*** Move to: $newPath",
      "@@",
      "-alpha",
      "+alpha updated",
      "*** End Patch",
    )

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file."
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(newPath))
      },
      renderNumberedText("alpha updated\nbeta\n")
    )

    assertReadFails(oldPath)
  }

  @Test
  fun apply_patch_moves_file_with_unchanged_content() = runBlocking(Dispatchers.Default) {
    val oldPath = "src/move-only.txt"
    val newPath = "src/moved/move-only.txt"

    testMcpTool(
      FileToolset::create_new_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(oldPath))
        put("text", JsonPrimitive("alpha\nbeta\n"))
      },
      "[success]"
    )

    val patch = buildPatch(
      "*** Begin Patch",
      "*** Update File: $oldPath",
      "*** Move to: $newPath",
      "@@",
      "-alpha",
      "+alpha",
      "*** End Patch",
    )

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file."
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(newPath))
      },
      renderNumberedText("alpha\nbeta\n")
    )

    assertReadFails(oldPath)
  }

  @Test
  fun apply_patch_supports_strict_at_at_pair_hunk_blocks() = runBlocking(Dispatchers.Default) {
    val pathInProject = "src/Test.java"
    val patch = buildPatch(
      "*** Begin Patch",
      "*** Update File: $pathInProject",
      "@@",
      "Test.java content",
      "@@",
      "updated with strict pair",
      "*** End Patch",
    )

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file."
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(pathInProject))
      },
      renderNumberedText("updated with strict pair")
    )
  }

  @Test
  fun apply_patch_supports_wrapped_unified_git_diff() = runBlocking(Dispatchers.Default) {
    val pathInProject = "src/Test.java"
    val patch = buildPatch(
      "*** Begin Patch",
      "diff --git a/$pathInProject b/$pathInProject",
      "--- a/$pathInProject",
      "+++ b/$pathInProject",
      "@@ -1 +1 @@",
      "-Test.java content",
      "+updated from git diff",
      "*** End Patch",
    )

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file."
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(pathInProject))
      },
      renderNumberedText("updated from git diff")
    )
  }

  @Test
  fun apply_patch_supports_raw_git_rename_without_hunks() = runBlocking(Dispatchers.Default) {
    val oldPath = "src/rename-old.txt"
    val newPath = "src/rename-new.txt"

    testMcpTool(
      FileToolset::create_new_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(oldPath))
        put("text", JsonPrimitive("alpha\nbeta\n"))
      },
      "[success]"
    )

    val patch = buildPatch(
      "diff --git a/$oldPath b/$newPath",
      "similarity index 100%",
      "rename from $oldPath",
      "rename to $newPath",
    )

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive(patch))
      },
      "Applied patch to 1 file."
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(newPath))
      },
      renderNumberedText("alpha\nbeta\n")
    )

    assertReadFails(oldPath)
  }

  @Test
  fun apply_patch_accepts_patch_alias_parameter() = runBlocking(Dispatchers.Default) {
    val pathInProject = "src/alias.txt"
    val patch = buildPatch(
      "*** Begin Patch",
      "*** Add File: $pathInProject",
      "+ok",
      "*** End Patch",
    )

    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("patch", JsonPrimitive(patch))
      },
      "Applied patch to 1 file."
    )

    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(pathInProject))
      },
      renderNumberedText("ok\n")
    )
  }

  @Test
  fun apply_patch_rejects_invalid_format() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      PatchToolset::apply_patch.name,
      buildJsonObject {
        put("input", JsonPrimitive("oops"))
      }
    ) { actualResult ->
      assertThat(actualResult.isError).isTrue()
      assertThat(actualResult.textContent.text).contains("*** Begin Patch")
    }
  }

  private suspend fun assertReadFails(pathInProject: String) {
    testMcpTool(
      ReadToolset::read_file.name,
      buildJsonObject {
        put("file_path", JsonPrimitive(pathInProject))
      }
    ) { actualResult ->
      assertThat(actualResult.isError).isTrue()
    }
  }

  private fun renderNumberedText(text: String): String {
    val lines = text.replace("\r\n", "\n").replace("\r", "\n").split('\n')
    return lines.mapIndexed { index, line -> "L${index + 1}: $line" }.joinToString("\n")
  }

  private fun buildPatch(vararg lines: String): String {
    return lines.joinToString(separator = "\n", postfix = "\n")
  }
}
