// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val REMOVE_FIX = PyPsiBundle.message("INSP.unknown.ignore.code.remove.fix")

@Subsystems.Inspections
@Layers.Functional
@PyCodeInsightTestCase.TestInspections(enableInspections = [PyUnknownIgnoreCodeInspection::class])
@TestFor(issues = ["PY-92114"], classes = [PyUnknownIgnoreCodeInspection::class])
class PyUnknownIgnoreCodeInspectionTest : PyCodeInsightTestCase() {

  @Test
  fun `unknown namespaced code flagged`() = test("""
    x = 1  # type: ignore[pycharm:amongus]
    #                     ^^^^^^^^^^^^^^^ WARNING Unknown inspection code 'amongus'
    """)

  @Test
  @TestFor(issues = ["PY-92114", "PY-90627"])
  fun `unknown code in pycharm ignore flagged`() = test("""
    x = 1  # pycharm: ignore[amongus]
    #                        ^^^^^^^ WARNING Unknown inspection code 'amongus'
    """)

  @Test
  fun `only the unknown code flagged`() = test("""
    x = 1  # type: ignore[attr-defined, pycharm:amongus, PyTypeChecker]
    #                                   ^^^^^^^^^^^^^^^ WARNING Unknown inspection code 'amongus'
    """)

  @Test
  fun `file level unknown code flagged`() = test("""
    # type: ignore[pycharm:amongus]
    #              ^^^^^^^^^^^^^^^ WARNING Unknown inspection code 'amongus'
    x = 1
    """)

  /** Other type checkers use their own codes in `# type: ignore`. */
  @Test
  fun `bare foreign code not flagged`() = test("""
    x = 1  # type: ignore[amongus]
    """)

  @Test
  fun `namespaced suppress id not flagged`() = test("""
    x = 1  # type: ignore[pycharm:PyTypeChecker]
    """)

  @Test
  fun `namespaced kebab alias not flagged`() = test("""
    x = 1  # type: ignore[pycharm:unresolved-references]
    """)

  @Test
  fun `namespaced granular code not flagged`() = test("""
    x = 1  # type: ignore[pycharm:unsupported-operator]
    """)

  @Test
  fun `known code in pycharm ignore not flagged`() = test("""
    x = 1  # pycharm: ignore[unsupported-operator, PyUnresolvedReferences]
    """)

  /** An inspection of another edition or of a disabled plugin can own such an id. */
  @Test
  fun `namespaced Py id not flagged`() = test("""
    x = 1  # type: ignore[pycharm:PyNotLoadedInspection]
    """)

  @Test
  fun `Py id in pycharm ignore not flagged`() = test("""
    x = 1  # pycharm: ignore[PyNotLoadedInspection]
    """)

  @Test
  fun `quick fix removes one of several codes`() = doQuickFixTest(
    before = "x = 1  # type: ignore[attr-defined, PyTypeChecker, pycharm:amo<caret>ngus]",
    after = "x = 1  # type: ignore[attr-defined, PyTypeChecker]",
  )

  /** Only foreign codes would stay, and such a comment suppresses every inspection on the line. */
  @Test
  fun `no quick fix when only foreign codes stay`() = runInEdtAndWait {
    withInspection {
      myFixture.configureByText(PythonFileType.INSTANCE, "x = 1  # type: ignore[attr-defined, pycharm:amo<caret>ngus]")
      assertTrue(myFixture.filterAvailableIntentions(REMOVE_FIX).isEmpty())
    }
  }

  @Test
  @TestFor(issues = ["PY-92114", "PY-90627"])
  fun `quick fix in pycharm ignore`() = doQuickFixTest(
    before = "x = 1  # pycharm: ignore[unsupported-operator, amo<caret>ngus]",
    after = "x = 1  # pycharm: ignore[unsupported-operator]",
  )

  @Test
  fun `quick fix removes the reported duplicate`() = doQuickFixTest(
    before = "x = 1  # type: ignore[pycharm:amongus, PyTypeChecker, pycharm:amo<caret>ngus]",
    after = "x = 1  # type: ignore[pycharm:amongus, PyTypeChecker]",
  )

  @Test
  fun `quick fix keeps a bracket inside another code`() = doQuickFixTest(
    before = "x = 1  # type: ignore[x[y, PyTypeChecker, pycharm:z<caret>z]",
    after = "x = 1  # type: ignore[x[y, PyTypeChecker]",
  )

  @Test
  fun `quick fix removes a file level comment`() = doQuickFixTest(
    before = "# type: ignore[pycharm:amo<caret>ngus]\nx = 1",
    after = "x = 1",
  )

  /** A bare comment would suppress every inspection on the line, so the fix removes the whole comment. */
  @Test
  fun `quick fix removes the comment with its only code`() = doQuickFixTest(
    before = "x = 1  # type: ignore[pycharm:amo<caret>ngus]",
    after = "x = 1",
  )

  @Test
  fun `quick fix keeps the trailing comment`() = doQuickFixTest(
    before = "x = 1  # type: ignore[pycharm:amo<caret>ngus]  # a note",
    after = "x = 1  # a note",
  )

  private fun doQuickFixTest(before: String, after: String) = runInEdtAndWait {
    withInspection {
      myFixture.configureByText(PythonFileType.INSTANCE, before)
      myFixture.launchAction(myFixture.findSingleIntention(REMOVE_FIX))
      myFixture.checkResult(after)
    }
  }

  private fun withInspection(body: () -> Unit) {
    val inspection = PyUnknownIgnoreCodeInspection()
    myFixture.enableInspections(inspection)
    try {
      body()
    }
    finally {
      myFixture.disableInspections(inspection)
    }
  }
}
