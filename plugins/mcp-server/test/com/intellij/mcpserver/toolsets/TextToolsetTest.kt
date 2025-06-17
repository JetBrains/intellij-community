@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.TextToolset
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import io.kotest.common.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class TextToolsetTest : McpToolsetTestBase() {
  @Test
  fun get_open_in_editor_file_text() = runBlocking {
    testMcpTool(
      TextToolset::get_open_in_editor_file_text.name,
      buildJsonObject {},
      ""
    )
  }

  // TODO: IMHO badly designed tool. Can junk LLM input. To remove in favor of list_editor_files and get_text_by_path
  @Test
  fun get_all_open_file_texts() = runBlocking {
    testMcpTool(
      TextToolset::get_all_open_file_texts.name,
      buildJsonObject {},
      "[]"
    )
  }

  @Test
  fun get_selected_in_editor_text() = runBlocking {
    testMcpTool(
      TextToolset::get_selected_in_editor_text.name,
      buildJsonObject {},
      ""
    )
  }

  @Test
  fun replace_selected_text() = runBlocking {
    testMcpTool(
      TextToolset::replace_selected_text.name,
      buildJsonObject {
        put("text", JsonPrimitive("replacement text"))
      },
      "no text selected"
    )
  }

  @Test
  fun replace_current_file_text() = runBlocking {
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(mainJavaFile, true)
    }
    testMcpTool(
      TextToolset::replace_current_file_text.name,
      buildJsonObject {
        put("text", JsonPrimitive("new file content"))
      },
      "ok"
    )
  }

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
  fun replace_specific_text() = runBlocking {
    testMcpTool(
      TextToolset::replace_specific_text.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(testJavaFile.name))
        put("oldText", JsonPrimitive("content"))
        put("newText", JsonPrimitive("updated content"))
      },
      "ok"
    )
  }

  @Test
  fun replace_file_text_by_path() = runBlocking {
    testMcpTool(
      TextToolset::replace_file_text_by_path.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(mainJavaFile.name))
        put("text", JsonPrimitive("updated content"))
      },
      "ok"
    )
  }
}