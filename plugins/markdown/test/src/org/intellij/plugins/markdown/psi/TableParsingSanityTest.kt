package org.intellij.plugins.markdown.psi

import com.intellij.idea.TestFor
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestFor(issues = ["IDEA-308695"])
@TestApplication
class TableParsingSanityTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test various cell widths are recognized`(): Unit = timeoutRunBlocking {
    val content = """
    | |  |    |   |    |   |    |  |  |
    |-|--|----|---|:---|:-:|---:|:-|-:|
    | |  |    |   |    |   |    |  |  |
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val element = myFixture.file.findElementAt(0)!!
      val table = TableUtils.findTable(element)
      checkNotNull(table) { "Content was not recognised as a valid table" }
      val rows = table.getRows(true)
      assertEquals(2, rows.size)
      for (row in rows) {
        assertEquals(9, row.cells.size)
      }
    }
  }

  @Test
  fun `test empty cells`(): Unit = timeoutRunBlocking {
    val content = """
    |||
    |-|-|
    |||
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val element = myFixture.file.findElementAt(0)!!
      val table = TableUtils.findTable(element)
      checkNotNull(table) { "Content was not recognised as a valid table" }
      val rows = table.getRows(true)
      assertEquals(2, rows.size)
      for (row in rows) {
        assertEquals(2, row.cells.size)
      }
      val separator = table.separatorRow!!
      assertEquals(2, separator.cellsCount)
    }
  }

  @Test
  fun `test empty cells with trailing whitespace`(): Unit = timeoutRunBlocking {
    val content = """
    ||| 
    |-|-|
    |||
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val element = myFixture.file.findElementAt(0)!!
      val table = TableUtils.findTable(element)
      checkNotNull(table) { "Content was not recognised as a valid table" }
      val rows = table.getRows(true)
      assertEquals(2, rows.size)
      for (row in rows) {
        assertEquals(2, row.cells.size)
      }
      val separator = table.separatorRow!!
      assertEquals(2, separator.cellsCount)
    }
  }
}
