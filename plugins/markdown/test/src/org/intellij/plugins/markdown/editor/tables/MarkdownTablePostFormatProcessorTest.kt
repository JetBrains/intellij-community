package org.intellij.plugins.markdown.editor.tables

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.performReformatting
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.runWithTemporaryStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/tables/format")
@TestFor(issues = ["IDEA-298828"])
class MarkdownTablePostFormatProcessorTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true).trimStart().replace(' ', '_')
  }

  @Test
  fun `single table`(): Unit = doTest()

  @Test
  fun `multiple tables`(): Unit = doTest()

  @Test
  fun `table without end newline`(): Unit = doTest()

  @Test
  fun `chinese table test`(): Unit = doTest()

  @Test
  fun `emoji table`(): Unit = doTest()

  @Test
  fun `table with colored boxes`(): Unit = doTest()

  @Test
  fun `emoji sequence table`(): Unit = doTest()

  @Test
  fun `table inside list item`(): Unit = doTest()

  @Test
  fun `table with tabs`(): Unit = doTest()

  @Test
  fun `table with tab inside cell text`(): Unit = doTest(tabSize = 4)

  @Test
  fun `tabs in code spans preserve alignment`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      for (tabSize in listOf(2, 4, 8)) {
        runWithTemporaryStyleSettings(myFixture.project) { settings ->
          settings.getIndentOptions(MarkdownFileType.INSTANCE).TAB_SIZE = tabSize
          val leftContent = " `a\tb`" + " ".repeat(if (tabSize == 2) 7 else 5)
          val rightContent = if (tabSize == 8) "    `a\tb`     " else "        `a\tb` "
          for ((separator, content) in listOf(
            ":-------------" to leftContent,
            "-------------:" to rightContent,
            ":------------:" to "    `a\tb`     ",
          )) {
            checkStyle(
              TableStyle.ALIGNED,
              "| abcdefghijkl | x |\n|$separator|---|\n| `a\tb` | y |",
              "| abcdefghijkl | x |\n|$separator|---|\n|$content| y |",
            )
            val table = checkNotNull(TableUtils.findTable(myFixture.file, 0))
            assertTrue(TableModificationUtils.run { table.isCorrectlyFormatted(TableStyle.ALIGNED) })
          }
        }
      }
    }
  }

  @Test
  fun `compact table`(): Unit = doStyleTest(
    TableStyle.COMPACT,
    """
    |Character|Meaning|
    |---------|-------|
    |Y|Yes|
    |N|No|
    """.trimIndent(),
    """
    | Character | Meaning |
    | --- | --- |
    | Y | Yes |
    | N | No |
    """.trimIndent()
  )

  @Test
  fun `tight table`(): Unit = doStyleTest(
    TableStyle.TIGHT,
    """
    | Character | Meaning |
    | --- | --- |
    | Y | Yes |
    | N | No |
    """.trimIndent(),
    """
    |Character|Meaning|
    |---|---|
    |Y|Yes|
    |N|No|
    """.trimIndent()
  )

  @Test
  fun `table style is applied when reformatting a file`(): Unit = timeoutRunBlocking {
    runWithTemporaryStyleSettings(myFixture.project) { settings ->
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).FORMAT_TABLES = true
      withTableStyle(myFixture.project, TableStyle.COMPACT) {
        myFixture.configureByText(
          "some.md",
          """
          |Character|Meaning|
          |---------|-------|
          |Y|Yes|
          """.trimIndent()
        )
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          CodeStyleManager.getInstance(myFixture.project).reformat(myFixture.file)
        }
        myFixture.checkResult(
          """
          | Character | Meaning |
          | --- | --- |
          | Y | Yes |
          """.trimIndent()
        )
      }
    }
  }

  @Test
  fun `reformat table after wrapped block quote does not throw`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val text = """
      > This is a long sentence that contains formatted text at a place that will be _reformatted_ and when it is reformatted, it will be reformatted in a way that
      > is not the way it should be done.

      | one | two | three | four |
      |-----|-----|-------|------|
      | 1 | 2 | 3 | 4 |
      | 5 | 6 | 7 | 8 |
    """.trimIndent()
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.getCommonSettings(MarkdownLanguage.INSTANCE).RIGHT_MARGIN = 80
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
          FORMAT_TABLES = true
          INSERT_QUOTE_ARROWS_ON_WRAP = true
        }
        myFixture.configureByText("some.md", text)
        performReformatting(myFixture.project, myFixture.file)
      }
    }
  }

  private fun doTest(tabSize: Int? = null): Unit = timeoutRunBlocking {
    val before = "$testName.before.md"
    val after = "$testName.after.md"
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.apply {
          if (tabSize != null) {
            getIndentOptions(MarkdownFileType.INSTANCE).TAB_SIZE = tabSize
          }
          getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
            FORMAT_TABLES = true
          }
        }
        myFixture.configureByFile(before)
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResultByFile(after)
        //check idempotence of formatter
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResultByFile(after)
      }
    }
  }

  private fun doStyleTest(style: TableStyle, before: String, after: String): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      checkStyle(style, before, after)
    }
  }

  @RequiresEdt
  private fun checkStyle(style: TableStyle, before: String, after: String) {
    runWithTemporaryStyleSettings(myFixture.project) { settings ->
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).FORMAT_TABLES = true
      withTableStyle(myFixture.project, style) {
        myFixture.configureByText("some.md", before)
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResult(after)
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResult(after)
      }
    }
  }
}
