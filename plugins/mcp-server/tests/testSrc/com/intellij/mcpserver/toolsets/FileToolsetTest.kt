@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.FileToolset
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class FileToolsetTest : GeneralMcpToolsetTestBase() {
  @Disabled("Output contains the project directory name that is not predictable because of generated")
  @Test
  fun list_directory_tree() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      FileToolset::list_directory_tree.name,
      buildJsonObject {
        put("directoryPath", JsonPrimitive("/"))
        put("maxDepth", JsonPrimitive(2))
      },
      """{"name":"IJ4720168971772072169","type":"directory","path":"","children":[{"name":"Class.java","type":"file","path":"Class.java"},{"name":"Test.java","type":"file","path":"Test.java"},{"name":"Main.java","type":"file","path":"Main.java"}]}"""
    )
  }

  @Test
  fun open_file_in_editor() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      FileToolset::open_file_in_editor.name,
      buildJsonObject {
        val projectPath = Path.of(project.basePath ?: error("Project base path is not available"))
        put("filePath", JsonPrimitive(projectPath.relativizeIfPossible(testJavaFile)))
      },
      "[success]"
    )
  }

  @Test
  fun get_all_open_file_paths() = runBlocking(Dispatchers.Default) {
    withContext(Dispatchers.EDT) {
      FileEditorManagerEx.getInstanceExAsync(project).openFile(mainJavaFile, true)
    }
    testMcpTool(
      FileToolset::get_all_open_file_paths.name,
      buildJsonObject {},
      """{"activeFilePath":"src/Main.java","openFiles":["src/Main.java"]}"""
    )
  }

  @Test
  fun create_new_file() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      FileToolset::create_new_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive("src/NewFile.java"))
      },
      "[success]"
    )
  }

  @Test
  fun create_new_file_saves_text_to_disk() = runBlocking(Dispatchers.Default) {
    val pathInProject = "src/NewFileWithText.txt"
    testMcpTool(
      FileToolset::create_new_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive(pathInProject))
        put("text", JsonPrimitive("persisted\n"))
      },
      "[success]"
    )

    val filePath = Path.of(project.basePath ?: error("Project base path is not available")).resolve(pathInProject)
    assertThat(Files.readString(filePath)).isEqualTo("persisted\n")
    Unit
  }
}
