// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.Filter
import com.intellij.idea.TestFor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText

/**
 * The links of the file locations in the console output of Ruff and ty.
 */
@TestFor(classes = [AstralPathFilter::class])
@Subsystems.Run
@Layers.Functional
class AstralPathFilterTest : PyCodeInsightTestCase() {

  @TempDir
  lateinit var workingDirectory: Path

  @Test
  fun `location in a file links to its line and column`() {
    createFile("src/module.py", "value = 1234567890\n".repeat(20))
    val line = "  --> src/module.py:16:9"

    val result = applyFilter(line)

    assertLink(result, line, "module.py", expectedLine = 15, expectedColumn = 8)
  }

  @Test
  @TestFor(issues = ["PY-92946"])
  fun `location in a notebook cell links to the notebook`() {
    createFile("notebooks/analysis.ipynb", NOTEBOOK_TEXT)
    val line = "  --> notebooks/analysis.ipynb:cell 3:3:1"

    val result = applyFilter(line)

    assertLink(result, line, "analysis.ipynb", expectedLine = 0, expectedColumn = 0)
  }

  @Test
  @TestFor(issues = ["PY-92946"])
  fun `location in a notebook cell with a Windows separator links to the notebook`() {
    createFile("notebooks/analysis.ipynb", NOTEBOOK_TEXT)
    val line = "  --> notebooks\\analysis.ipynb:cell 12:3:1"

    val result = applyFilter(line)

    assertLink(result, line, "analysis.ipynb", expectedLine = 0, expectedColumn = 0)
  }

  @Test
  @TestFor(issues = ["PY-92946"])
  fun `absolute location in a notebook cell links to the notebook`() {
    createFile("notebooks/analysis.ipynb", NOTEBOOK_TEXT)
    val line = "  --> ${workingDirectory.pathString}/notebooks/analysis.ipynb:cell 3:3:1"

    val result = applyFilter(line)

    assertLink(result, line, "analysis.ipynb", expectedLine = 0, expectedColumn = 0)
  }

  @Test
  @TestFor(issues = ["PY-92946"])
  fun `location with a file name that is not a valid path gets no link`() {
    assertNull(applyFilter("  --> src/mod\u0000ule.py:1:1"))
  }

  private fun createFile(relativePath: String, text: String) {
    workingDirectory.resolve(relativePath).also { it.parent.createDirectories() }.writeText(text)
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(workingDirectory)!!.refresh(false, true)
  }

  private fun applyFilter(line: String): Filter.Result? = timeoutRunBlocking {
    readAction {
      AstralPathFilter(myFixture.project, workingDirectory.pathString).applyFilter(line, line.length)
    }
  }

  private fun assertLink(result: Filter.Result?, line: String, expectedFileName: String, expectedLine: Int, expectedColumn: Int) {
    assertNotNull(result, "Expected a link in '$line'")
    val item = result.resultItems.single()
    assertEquals(line.indexOf("-->") + 4, item.highlightStartOffset)
    assertEquals(line.length, item.highlightEndOffset)
    val (fileName, position) = timeoutRunBlocking {
      readAction {
        val descriptor = (item.hyperlinkInfo as FileHyperlinkInfo).descriptor!!
        val document = FileDocumentManager.getInstance().getDocument(descriptor.file)!!
        val lineNumber = document.getLineNumber(descriptor.offset)
        descriptor.file.name to (lineNumber to descriptor.offset - document.getLineStartOffset(lineNumber))
      }
    }
    assertEquals(expectedFileName, fileName)
    assertEquals(expectedLine to expectedColumn, position)
  }

  private companion object {
    const val NOTEBOOK_TEXT = "{\n  \"cells\": []\n}\n"
  }
}
