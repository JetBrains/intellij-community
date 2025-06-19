@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.FileToolset
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import io.kotest.common.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class FileToolsetTest : McpToolsetTestBase() {
  @Disabled("Output contains the project directory name that is not predictable because of generated")
  @Test
  fun list_directory_tree_in_folder() = runBlocking {
    testMcpTool(
      FileToolset::list_directory_tree_in_folder.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive("/"))
        put("maxDepth", JsonPrimitive(2))
      },
      """{"name":"IJ4720168971772072169","type":"directory","path":"","children":[{"name":"Class.java","type":"file","path":"Class.java"},{"name":"Test.java","type":"file","path":"Test.java"},{"name":"Main.java","type":"file","path":"Main.java"}]}"""
    )
  }

  @Disabled("Flaky")
  @Test
  fun list_files_in_folder() = runBlocking {
    testMcpTool(
      FileToolset::list_files_in_folder.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive("/"))
      },
"""[{"name": "Class.java", "type": "file", "path": "Class.java"},
{"name": "Test.java", "type": "file", "path": "Test.java"},
{"name": "Main.java", "type": "file", "path": "Main.java"}]"""
    )
  }

  @Test
  fun find_files_by_name_substring() = runBlocking {
    testMcpTool(
      FileToolset::find_files_by_name_substring.name,
      buildJsonObject {
        put("nameSubstring", JsonPrimitive("test"))
      },
      """[{"path": "Test.java", "name": "Test.java"}]"""
    )
  }

  @Test
  fun create_new_file_with_text() = runBlocking {
    testMcpTool(
      FileToolset::create_new_file_with_text.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive("test/test_file.txt"))
        put("text", JsonPrimitive("This is a test file"))
      },
      "ok"
    )
  }

  @Test
  fun open_file_in_editor() = runBlocking {
    testMcpTool(
      FileToolset::open_file_in_editor.name,
      buildJsonObject {
        put("filePath", JsonPrimitive("Test.java"))
      },
      "file is opened"
    )
  }

  @Test
  fun get_all_open_file_paths() = runBlocking {
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(mainJavaFile, true)
    }
    testMcpTool(
      FileToolset::get_all_open_file_paths.name,
      buildJsonObject {},
      "Main.java"
    )
  }

  @Test
  fun get_open_in_editor_file_path() = runBlocking {
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(mainJavaFile, true)
    }
    testMcpTool(
      FileToolset::get_open_in_editor_file_path.name,
      buildJsonObject {}
    ) { result ->
      assert(result.textContent.text?.endsWith("Main.java") == true)
    }
  }
}
