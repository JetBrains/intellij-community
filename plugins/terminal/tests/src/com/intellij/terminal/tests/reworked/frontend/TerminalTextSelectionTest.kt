package com.intellij.terminal.tests.reworked.frontend

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalOutputFileType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * See [com.intellij.terminal.frontend.TerminalTextSelectioner]
 */
@RunWith(JUnit4::class)
internal class TerminalTextSelectionTest : BasePlatformTestCase() {
  @Test
  fun `Unix file path is fully selected`() = doTest(
    before = "123 ~/documents/<caret>directory/file-name_123.txt some text",
    after = "123 <selection>~/documents/<caret>directory/file-name_123.txt</selection> some text",
  )

  @Test
  fun `Windows file path is fully selected`() = doTest(
    before = """123 C:\\ProgramFiles\\<caret>Documents\\File-name_123.txt some text""",
    after = """123 <selection>C:\\ProgramFiles\\<caret>Documents\\File-name_123.txt</selection> some text""",
  )

  @Test
  fun `all text is selected when caret is at the separator`() = doTest(
    before = "text <caret> 123\n456",
    after = "<selection>text <caret> 123\n456</selection>",
  )

  @Test
  fun `line break and tabulation is considered as a separator`() = doTest(
    before = "text\t1<caret>23\n456",
    after = "text\t<selection>1<caret>23</selection>\n456"
  )

  @Test
  fun `no-break space is considered as a separator`() = doTest(
    before = "aaa\u00A0bbb<caret>ccc text",
    after = "aaa\u00A0<selection>bbb<caret>ccc</selection> text",
  )

  @Test
  fun `single quote is considered as a separator`() = doTest(
    before = "start 'pa-<caret>th_1/file' end",
    after = "start '<selection>pa-<caret>th_1/file</selection>' end",
  )

  @Test
  fun `double quote is considered as a separator`() = doTest(
    before = """start "pa-<caret>th_1/file" end""",
    after = """start "<selection>pa-<caret>th_1/file</selection>" end""",
  )

  @Test
  fun `dollar sign is considered as a separator`() = doTest(
    before = $$"echo $PA<caret>TH 123",
    after = "echo $<selection>PA<caret>TH</selection> 123",
  )

  @Test
  fun `parentheses are considered as separators`() = doTest(
    before = "run (fi<caret>le) now",
    after = "run (<selection>fi<caret>le</selection>) now",
  )

  @Test
  fun `square brackets are considered as separators`() = doTest(
    before = "run [fi<caret>le] now",
    after = "run [<selection>fi<caret>le</selection>] now",
  )

  @Test
  fun `curly braces are considered as separators`() = doTest(
    before = "run {fi<caret>le} now",
    after = "run {<selection>fi<caret>le</selection>} now",
  )

  @Test
  fun `angle brackets are considered as separators`() = doTest(
    before = "run <fi<caret>le> now",
    after = "run <<selection>fi<caret>le</selection>> now",
  )

  private fun doTest(before: String, after: String) {
    myFixture.configureByText(TerminalOutputFileType, before)
    myFixture.editor.caretModel.currentCaret.selectWordAtCaret(false)
    myFixture.checkResult(after)
  }
}