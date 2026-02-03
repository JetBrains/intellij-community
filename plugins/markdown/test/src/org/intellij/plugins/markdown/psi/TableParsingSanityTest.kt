package org.intellij.plugins.markdown.psi

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow

@TestFor(issues = ["IDEA-308695"])
class TableParsingSanityTest: LightPlatformCodeInsightTestCase() {
  fun `test various cell widths are recognized`() {
    val content = """
    | |  |    |   |    |   |    |  |  |
    |-|--|----|---|:---|:-:|---:|:-|-:|
    | |  |    |   |    |   |    |  |  |
    """.trimIndent()
    configureFromFileText("some.md", content)
    val element = file.findElementAt(0)!!
    val table = TableUtils.findTable(element)
    checkNotNull(table) { "Content was not recognised as a valid table" }
    val rows = table.getRows(true)
    TestCase.assertEquals(2, rows.size)
    for (row in rows) {
      TestCase.assertEquals(9, row.cells.size)
    }
  }

  fun `test empty cells`() {
    val content = """
    |||
    |-|-|
    |||
    """.trimIndent()
    configureFromFileText("some.md", content)
    val element = file.findElementAt(0)!!
    val table = TableUtils.findTable(element)
    checkNotNull(table) { "Content was not recognised as a valid table" }
    val rows = table.getRows(true)
    TestCase.assertEquals(2, rows.size)
    for (row in rows) {
      TestCase.assertEquals(2, row.cells.size)
    }
    val separator = table.separatorRow!!
    TestCase.assertEquals(2, separator.cellsCount)
  }

  fun `test empty cells with trailing whitespace`() {
    val content = """
    ||| 
    |-|-|
    |||
    """.trimIndent()
    configureFromFileText("some.md", content)
    val element = file.findElementAt(0)!!
    val table = TableUtils.findTable(element)
    checkNotNull(table) { "Content was not recognised as a valid table" }
    val rows = table.getRows(true)
    TestCase.assertEquals(2, rows.size)
    for (row in rows) {
      TestCase.assertEquals(2, row.cells.size)
    }
    val separator = table.separatorRow!!
    TestCase.assertEquals(2, separator.cellsCount)
  }
}
