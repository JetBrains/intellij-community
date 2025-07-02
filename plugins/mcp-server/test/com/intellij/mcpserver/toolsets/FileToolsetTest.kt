@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.FileToolset
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
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
  fun list_directory_tree() = runBlocking {
    testMcpTool(
      FileToolset::list_directory_tree.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive("/"))
        put("maxDepth", JsonPrimitive(2))
      },
      """{"name":"IJ4720168971772072169","type":"directory","path":"","children":[{"name":"Class.java","type":"file","path":"Class.java"},{"name":"Test.java","type":"file","path":"Test.java"},{"name":"Main.java","type":"file","path":"Main.java"}]}"""
    )
  }

  @Test
  fun find_files_by_name_keyword() = runBlocking {
    testMcpTool(
      FileToolset::find_files_by_name_keyword.name,
      buildJsonObject {
        put("nameSubstring", JsonPrimitive("test"))
      },
      """[{"path": "Test.java", "name": "Test.java"}]"""
    )
  }
  @Test
  fun find_files_by_glob() = runBlocking {
    testMcpTool(
      FileToolset::find_files_by_glob.name,
      buildJsonObject {
        put("globPattern", JsonPrimitive("**/*.java"))
      },
      """[{"path": "Test.java", "name": "Test.java"}]"""
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
      FileEditorManagerEx.getInstanceExAsync(project).openFile(mainJavaFile, true)
    }
    testMcpTool(
      FileToolset::get_all_open_file_paths.name,
      buildJsonObject {},
      "Main.java"
    )
  }

  @Test
  fun create_new_file() = runBlocking {
    testMcpTool(
      FileToolset::create_new_file.name,
      buildJsonObject {},
      "Main.java"
    )
  }
}
