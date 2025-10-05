package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.RefactoringToolset
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import io.kotest.common.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class RefactoringToolsetTest : McpToolsetTestBase() {
  val mainJavaFileFixtureWithCode = sourceRootFixture.virtualFileFixture("MainWithCode.java", "public class MainWithCode { }")
  val mainJavaFileWithCode by mainJavaFileFixtureWithCode

  @Test
  @Disabled("symbol not found: MainWithCode in")
  fun rename_class() = runBlocking {
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(mainJavaFileWithCode, true)
    }
    testMcpTool(
      RefactoringToolset::rename_refactoring.name,
      buildJsonObject {
        put("symbolName", JsonPrimitive("MainWithCode"))
        put("newName", JsonPrimitive("NewMain"))
        put("pathInProject", JsonPrimitive(mainJavaFileWithCode.path))
      },
      "Successfully renamed 'MainWithCode' to 'NewMain' in ${mainJavaFileWithCode.path}"
    )

  }
}