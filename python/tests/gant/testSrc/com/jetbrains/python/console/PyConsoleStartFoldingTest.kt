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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
