// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.idea.TestFor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The expected texts of a hugged argument are what Black 26 with `--unstable` and Ruff 0.17 with `--preview` write.
 * Where the argument is not hugged, the Black-like profile also adds a trailing comma after a sole argument,
 * and Black and Ruff do not. Each test also checks that a second reformat changes nothing.
 */
@Subsystems.Formatter
@Layers.Functional
@TestFor(classes = [PyBlock::class], issues = ["PY-94479"])
class PyHugSoleCollectionArgumentFormatterTest : PyCodeInsightTestCase() {

  @Test
  fun `brackets of a sole list argument go next to the parentheses`() = assertReformatted("""
    print([12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123412, 1])
  """, """
    print([
        12341234123412,
        12341234123412,
        12341234123412,
        12341234123412,
        12341234123412,
        12341234123412,
        1,
    ])
  """)

  @Test
  fun `split argument list around a sole list argument joins its brackets`() = assertReformatted("""
    print(
        [
            12341234123412,
            12341234123412,
            12341234123412,
        ]
    )
  """, """
    print([
        12341234123412,
        12341234123412,
        12341234123412,
    ])
  """)

  @Test
  fun `short sole collection argument goes on one line with the parentheses`() = assertReformatted("""
    foo(
        [1, 2]
    )
  """, """
    foo([1, 2])
  """)

  @Test
  fun `brackets of each kind of sole collection argument go next to the parentheses`() = assertReformatted("""
    foo({"aaaaaaaaaaaaaaa": 1, "bbbbbbbbbbbbbbbbbbbbbbbbb": 2, "ccccccccccccccccccccccccccc": 3, "ddd": 4})
    foo({12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123412, 123412341234})
    foo((12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123))
    foo(*[12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123412])
    foo(**{"aaaaaaaaaaaaaaa": 1, "bbbbbbbbbbbbbbbbbbbbbbbbb": 2, "ccccccccccccccccccccccccccc": 3, "d": 4})
  """, """
    foo({
        "aaaaaaaaaaaaaaa": 1,
        "bbbbbbbbbbbbbbbbbbbbbbbbb": 2,
        "ccccccccccccccccccccccccccc": 3,
        "ddd": 4,
    })
    foo({
        12341234123412,
        12341234123412,
        12341234123412,
        12341234123412,
        12341234123412,
        123412341234,
    })
    foo((
        12341234123412,
        12341234123412,
        12341234123412,
        12341234123412,
        12341234123412,
        12341234123,
    ))
    foo(*[
        12341234123412,
        12341234123412,
        12341234123412,
        12341234123412,
        12341234123412,
        12341234123412,
    ])
    foo(**{
        "aaaaaaaaaaaaaaa": 1,
        "bbbbbbbbbbbbbbbbbbbbbbbbb": 2,
        "ccccccccccccccccccccccccccc": 3,
        "d": 4,
    })
  """)

  @Test
  fun `argument that is not a sole positional collection keeps the usual layout`() = assertReformatted("""
    foo(key=[12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123412, 123412341])
    foo(bar(12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123412, 1234123412))
    foo(
        [
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
        ],
    )
  """, """
    foo(
        key=[
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            123412341,
        ],
    )
    foo(
        bar(
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            1234123412,
        ),
    )
    foo(
        [
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
        ],
    )
  """)

  @Test
  fun `comprehension, f-string fragment and comment keep the usual layout`() = assertReformatted("""
    foo([x for x in y])
    f"{foo( [1, 2] )=}"
    foo(  # comment
        [1, 2]
    )
  """, """
    foo([x for x in y])
    f"{foo( [1, 2] )=}"
    foo(  # comment
        [1, 2],
    )
  """)

  @Test
  fun `hugged list in a function body keeps the body indent`() = assertReformatted("""
    def f():
        return foo([12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123412])
  """, """
    def f():
        return foo([
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
        ])
  """)

  @Test
  fun `enter after the opening parenthesis indents the argument`() = runInEdtAndWait {
    myFixture.configureByText("a.py", """
      def g():
          print(<caret>[1, 2])
    """.trimIndent())
    val settings = CodeStyle.createTestSettings()
    PyDefaultStyleGuide.apply(settings)
    CodeStyle.doWithTemporarySettings(myFixture.project, settings, Runnable {
      myFixture.type("\n")
    })
    assertEquals("""
      def g():
          print(
              [1, 2])
    """.trimIndent(), myFixture.file.text.trimEnd())
  }

  @Test
  fun `option off keeps the usual layout`() = assertReformatted("""
    print([12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123412, 12341234123412, 1])
  """, """
    print(
        [
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            12341234123412,
            1,
        ],
    )
  """) { it.HUG_SOLE_COLLECTION_ARGUMENT = false }

  private fun assertReformatted(before: String, expected: String, configure: (PyCodeStyleSettings) -> Unit = {}) {
    reformat(before.trimIndent(), configure)
    assertEquals(expected.trimIndent(), myFixture.file.text.trimEnd())
    reformat(myFixture.file.text, configure)
    assertEquals(expected.trimIndent(), myFixture.file.text.trimEnd(), "A second reformat changed the text")
  }

  private fun reformat(text: String, configure: (PyCodeStyleSettings) -> Unit) {
    myFixture.configureByText("a.py", text)
    val settings = CodeStyle.createTestSettings()
    PyDefaultStyleGuide.apply(settings)
    configure(settings.getCustomSettings(PyCodeStyleSettings::class.java))
    CodeStyle.doWithTemporarySettings(myFixture.project, settings, Runnable {
      WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
        CodeStyleManager.getInstance(myFixture.project).reformat(myFixture.file)
      })
    })
  }
}
