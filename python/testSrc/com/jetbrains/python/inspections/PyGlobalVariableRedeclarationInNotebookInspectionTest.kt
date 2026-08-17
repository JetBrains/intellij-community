// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The inspection only looks at files whose name ends in `ipynb`, so the tests configure `aaa.ipynb` and have that
 * extension parsed as Python: the notebook PSI itself lives outside the community sources.
 */
@TestFor(issues = ["PY-91546"], classes = [PyGlobalVariableRedeclarationInNotebookInspection::class])
@Subsystems.CodeInsight
@Layers.Functional
@PyCodeInsightTestCase.TestInspections(
  enableInspections = [PyGlobalVariableRedeclarationInNotebookInspection::class],
  // every redeclaration these tests write is also an unused one, and that warning is not what is under test here
  disableInspections = [PyRedeclarationInspection::class],
)
class PyGlobalVariableRedeclarationInNotebookInspectionTest : PyCodeInsightTestCase() {

  @BeforeEach
  fun parseNotebooksAsPython() {
    CodeInsightTestFixtureImpl.associateExtensionTemporarily(PythonFileType.INSTANCE, "ipynb", myFixture.testRootDisposable)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a top level variable assigned twice is reported at its second assignment`() {
    test("""
      x = 1
      x = 2 # WEAK-WARNING Global variable 'x' is already defined in this notebook
    """)
  }

  @Test
  fun `a top level variable assigned twice in a plain python file is not reported`() {
    test("""
      x = 1
      x = 2
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a qualified target is not reported`() {
    test("""
      class C:
          x = 0

      c = C()
      c.x = 1
      c.x = 2
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `an underscore target is not reported`() {
    test("""
      _ = 1
      _ = 2
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a variable shadowing a global one inside a function is not reported`() {
    test("""
      x = 1

      def f():
          x = 2
          return x
    """)
  }

  // The second assignment reads the variable it assigns, so the value depends on how often the cell was run
  // anyway and warning about it would only be noise.
  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a variable assigned from itself is not reported`() {
    test("""
      x = 1
      x = x + 1
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a loop target is not reported`() {
    test("""
      i = 0
      for i in range(3):
          print(i)
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a variable used as a loop body target is reported`() {
    test("""
      total = 0
      for i in range(3):
          total = i # WEAK-WARNING Global variable 'total' is already defined in this notebook
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `variables named after plotting objects are not reported`() {
    test("""
      fig = 1
      fig = 2
      ax = 1
      ax = 2
      axs = 1
      axs = 2
      axes = 1
      axes = 2
      g = 1
      g = 2
      grid = 1
      grid = 2
      p = 1
      p = 2
      plot = 1
      plot = 2
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a variable holding a matplotlib figure is not reported`() {
    addMatplotlibStub()
    test("""
      from matplotlib.figure import Figure

      chart = Figure()
      chart = Figure()
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a variable holding matplotlib axes is not reported`() {
    addMatplotlibStub()
    test("""
      from matplotlib.axes import Subplot

      panel = Subplot()
      panel = Subplot()
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a variable holding a plotly figure is not reported`() {
    addPlotlyStub()
    test("""
      from plotly.graph_objects import Figure

      chart = Figure()
      chart = Figure()
    """)
  }

  // Only matplotlib and plotly figures are exempt, a class from another library is reported as any other value.
  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a variable holding an instance of an unrelated class is reported`() {
    addMatplotlibStub()
    test("""
      from matplotlib.backends import Canvas

      chart = Canvas()
      chart = Canvas() # WEAK-WARNING Global variable 'chart' is already defined in this notebook
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a variable holding a function is reported`() {
    test("""
      def f():
          pass

      handler = f
      handler = f # WEAK-WARNING Global variable 'handler' is already defined in this notebook
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `a variable assigned after a bare type declaration is reported`() {
    test("""
      x: int
      x = 1 # WEAK-WARNING Global variable 'x' is already defined in this notebook
    """)
  }

  @Test
  @TestCaseOptions(testFileName = "aaa.ipynb")
  fun `the suppression fix comments the redeclaring statement out of the inspection`() {
    testQuickFix("""
      x = 1
      x = 2
    """, "Suppress for this statement", """
      x = 1
      # noinspection PyGlobalVariableRedeclarationInNotebook
      x = 2
    """)
  }

  /** Only the qualified names of the classes matter to the inspection, so the stubbed bodies stay empty. */
  private fun addMatplotlibStub() {
    myFixture.addFileToProject("matplotlib/__init__.py", "")
    myFixture.addFileToProject("matplotlib/figure.py", "class Figure:\n    pass\n")
    myFixture.addFileToProject("matplotlib/axes/__init__.py", "class Subplot:\n    pass\n")
    myFixture.addFileToProject("matplotlib/backends/__init__.py", "class Canvas:\n    pass\n")
  }

  private fun addPlotlyStub() {
    myFixture.addFileToProject("plotly/__init__.py", "")
    myFixture.addFileToProject("plotly/graph_objects.py", "class Figure:\n    pass\n")
  }
}
