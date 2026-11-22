// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.idea.TestFor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Subsystems.PythonConsole
@Layers.Functional
@TestFor(classes = [PyConsoleStartFolding::class])
class PyConsoleStartFoldingTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(issues = ["PY-92928"])
  fun `a carriage return before the banner removes text after the start offset`() = runInEdtAndWait {
    val console = PythonConsoleView(myFixture.project, "test", null, false)
    // The console releases its editors on disposal, which must run on the EDT.
    try {
      // The editor of the console exists only after the first call of `getComponent`.
      console.component
      val editor = console.editor!!
      console.print("log\nwait", ConsoleViewContentType.NORMAL_OUTPUT)
      console.flushDeferredText()
      val folding = PyConsoleStartFolding(console, false)
      folding.setStartLineOffset(editor.document.textLength)
      editor.document.addDocumentListener(folding, console)

      console.print("\rPython 3.12\n[GCC]\n>>> ", ConsoleViewContentType.NORMAL_OUTPUT)
      console.flushDeferredText()

      val region = editor.foldingModel.allFoldRegions.single()
      assertEquals(TextRange(4, 21), region.textRange)
      assertEquals("Python 3.12", region.placeholderText)
    }
    finally {
      Disposer.dispose(console)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["repr", "[1, 2]"])
  @TestFor(issues = ["PY-94113"])
  fun `a line printed after a one-line banner in the Debug Console stays visible`(line: String) = withConsole { console ->
    console.printNow("$START_COMMAND\n")
    console.addStartFolding(console.editor!!.document.textLength)
    console.printNow("$ONE_LINE_BANNER\n")

    val lineOffset = console.printNow("$line\n")

    val foldingModel = console.editor!!.foldingModel
    assertThat(foldingModel.isOffsetCollapsed(lineOffset))
      .describedAs("the line after the banner is collapsed, fold regions: %s", foldingModel.allFoldRegions.toList())
      .isFalse()
  }

  @Test
  @TestFor(issues = ["PY-94113"])
  fun `the first command after a one-line banner in the Python Console stays visible`() = withConsole { console ->
    console.addStartFolding(0)
    console.printNow("$START_COMMAND\nsys.path.extend(['/project'])\n")
    console.printNow("PyDev console: starting.\n\n")
    console.printNow("$ONE_LINE_BANNER\n")

    val commandOffset = console.printNow("first_command = 1\n")

    val foldingModel = console.editor!!.foldingModel
    assertThat(foldingModel.isOffsetCollapsed(commandOffset))
      .describedAs("the command after the banner is collapsed, fold regions: %s", foldingModel.allFoldRegions.toList())
      .isFalse()
  }

  @Test
  @TestFor(issues = ["PY-94113"])
  fun `a one-line banner after a carriage return stays unfolded`() = withConsole { console ->
    console.printNow("log\nwait")
    console.addStartFolding(console.editor!!.document.textLength)

    console.printNow("\r$ONE_LINE_BANNER\n>>> ")

    // The carriage return replaced "wait", so the banner starts before the start offset of the folding.
    val bannerOffset = console.editor!!.document.text.indexOf(ONE_LINE_BANNER)
    assertThat(bannerOffset).isEqualTo("log\n".length)
    val foldingModel = console.editor!!.foldingModel
    assertThat(foldingModel.isOffsetCollapsed(bannerOffset))
      .describedAs("the banner is collapsed, fold regions: %s", foldingModel.allFoldRegions.toList())
      .isFalse()
  }

  @Test
  @TestFor(issues = ["PY-94113"])
  fun `a two-line banner folds into its first line and no further`() = withConsole { console ->
    console.printNow("$START_COMMAND\n")
    console.addStartFolding(console.editor!!.document.textLength)
    val bannerOffset = console.printNow("Python 3.8.10 (default, Nov 22 2023, 10:22:35) \n[GCC 9.4.0] on linux\n")

    // A line that starts with "[" ends the fold only as the second line of a banner.
    val listOffset = console.printNow("[1, 2]\n")

    val regions = console.editor!!.foldingModel.allFoldRegions.map { Triple(it.textRange, it.placeholderText, it.isExpanded) }
    assertThat(regions).containsExactly(
      Triple(TextRange(bannerOffset, listOffset - 1), "Python 3.8.10 (default, Nov 22 2023, 10:22:35) ", false))
  }

  /** Runs [body] on the EDT with a new Python console, and disposes the console afterwards. */
  private fun withConsole(body: (PythonConsoleView) -> Unit): Unit = runInEdtAndWait {
    val console = PythonConsoleView(myFixture.project, "test", null, false)
    try {
      // The editor of the console exists only after the first call of `getComponent`.
      console.component
      body(console)
    }
    finally {
      Disposer.dispose(console)
    }
  }

  private fun PythonConsoleView.addStartFolding(startOffset: Int) {
    val folding = PyConsoleStartFolding(this, false)
    folding.setStartLineOffset(startOffset)
    editor!!.document.addDocumentListener(folding, this)
  }

  /** Prints [text] to the console document at once and returns the offset where it starts. */
  private fun PythonConsoleView.printNow(text: String): Int {
    val offset = editor!!.document.textLength
    print(text, ConsoleViewContentType.NORMAL_OUTPUT)
    flushDeferredText()
    return offset
  }

  private companion object {
    const val START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))"
    const val ONE_LINE_BANNER = "Python 3.12.11 (main, Jun  3 2025, 15:41:47) [Clang 17.0.0 (clang-1700.0.13.3)] on darwin"
  }
}
