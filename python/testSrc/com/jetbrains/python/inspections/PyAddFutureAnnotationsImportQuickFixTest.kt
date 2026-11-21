// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.quickfix.PyAddFutureAnnotationsImportQuickFix
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Test

@Subsystems.Inspections
@Layers.Functional
@TestFor(issues = ["PY-48304"], classes = [PyAddFutureAnnotationsImportQuickFix::class])
class PyAddFutureAnnotationsImportQuickFixTest : PyCodeInsightTestCase() {
  private val quickFixName = "Add 'from __future__ import annotations'"

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `method annotation names its own class`() = testQuickFix(
    """
    class Foo:
        def copy(self) -> F<caret>oo:
            ...
    """.trimIndent(),
    quickFixName,
    """
    from __future__ import annotations


    class Foo:
        def copy(self) -> Foo:
            ...
    """.trimIndent(),
  )

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `variable annotation names a class that the module defines later`() = testQuickFix(
    """
    a: A<caret> | None = None
    class A: ...
    """.trimIndent(),
    quickFixName,
    """
    from __future__ import annotations

    a: A | None = None
    class A: ...
    """.trimIndent(),
  )

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `import goes after the module docstring and joins an existing future import`() = testQuickFix(
    """
    $tripleQuote Shapes. $tripleQuote
    from __future__ import division


    class Shape:
        def scaled(self) -> Sha<caret>pe:
            ...
    """.trimIndent(),
    quickFixName,
    """
    $tripleQuote Shapes. $tripleQuote
    from __future__ import division, annotations


    class Shape:
        def scaled(self) -> Shape:
            ...
    """.trimIndent(),
  )

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `annotation in a method body names an attribute of the class`() = assertNoQuickFix(
    """
    class C:
        Alias = int

        def m(self):
            value: Alias = 1
    """.trimIndent(),
    quickFixName,
  )

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `annotation names a class that no scope defines`() = assertNoQuickFix(
    """
    def f() -> Missing:
        ...
    """.trimIndent(),
    quickFixName,
  )

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `name outside an annotation`() = assertNoQuickFix(
    """
    alias = Later
    class Later: ...
    """.trimIndent(),
    quickFixName,
  )

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON314)
  fun `python 3_14 defers annotations`() = assertNoQuickFix(
    """
    class Foo:
        def copy(self) -> Foo:
            ...
    """.trimIndent(),
    quickFixName,
  )
}
