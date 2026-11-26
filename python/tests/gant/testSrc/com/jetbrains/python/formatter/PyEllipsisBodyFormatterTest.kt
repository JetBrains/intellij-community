// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.idea.TestFor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Subsystems.Formatter
@Layers.Functional
@TestFor(classes = [PyBlock::class], issues = ["PY-92588"])
class PyEllipsisBodyFormatterTest : PyCodeInsightTestCase() {

  @Test
  fun `ellipsis body of a function goes on the line of the colon`() = assertReformatted("""
    def f():
        ...


    async def g():
        ...


    def outer():
        def inner():
            ...

        return inner


    if TYPE_CHECKING:
        def checked():
            ...
  """, """
    def f(): ...


    async def g(): ...


    def outer():
        def inner(): ...

        return inner


    if TYPE_CHECKING:
        def checked(): ...
  """)

  @Test
  fun `ellipsis body of a class and its methods goes on the line of the colon`() = assertReformatted("""
    class A:
        ...


    @decorator
    class B[T](A):
        ...


    class C:
        def m(self):
            ...

        # comment of the class body
        def n(self):
            ...
  """, """
    class A: ...


    @decorator
    class B[T](A): ...


    class C:
        def m(self): ...

        # comment of the class body
        def n(self): ...
  """)

  @Test
  fun `ellipsis bodies of overloads go on the line of the colon`() = assertReformatted("""
    from typing import overload


    @overload
    def f(x: int) -> int:
        ...


    @overload
    def f(x: str) -> str:
        ...


    def f(x):
        return x
  """, """
    from typing import overload


    @overload
    def f(x: int) -> int: ...


    @overload
    def f(x: str) -> str: ...


    def f(x):
        return x
  """)

  @Test
  fun `comment on the line of the ellipsis moves with it`() = assertReformatted("""
    def f():
        ...  # comment
  """, """
    def f(): ...  # comment
  """)

  @Test
  fun `comments around the ellipsis keep the body on its own line`() = assertUnchanged("""
    def leading():
        # comment
        ...


    def on_colon_line():  # comment
        ...


    def trailing():
        ...
        # comment


    def on_both_lines():
        ...  # first
        # second
  """)

  @Test
  fun `body that is not a lone ellipsis stays on its own line`() = assertUnchanged("""
    def with_docstring():
        '''Docstring.'''
        ...


    def two_ellipses():
        ...
        ...


    def with_pass():
        pass
  """)

  @Test
  fun `ellipsis body of a compound statement stays on its own line`() = assertUnchanged("""
    if True:
        ...

    for x in []:
        ...

    try:
        ...
    except Exception:
        ...

    with open("f"):
        ...
  """)

  @Test
  fun `ellipsis body goes on the line of the colon in a stub file`() = assertReformatted("""
    class A:
        def m(self):
            ...

        def n(self):
            # comment
            ...
  """, """
    class A:
        def m(self): ...

        def n(self):
            # comment
            ...
  """, fileName = "a.pyi")

  @Test
  fun `option takes precedence over a new line after the colon`() = assertReformatted("""
    def f(): ...
    if True: ...
  """, """
    def f(): ...


    if True:
        ...
  """) { it.NEW_LINE_AFTER_COLON = true }

  @Test
  fun `option off keeps the ellipsis body on its own line`() = assertUnchanged("""
    def f():
        ...


    def g(): ...
  """) { it.ELLIPSIS_BODY_ON_SAME_LINE = false }

  @Test
  fun `default profile chops down a long signature before the ellipsis body`() {
    val settings = CodeStyle.createTestSettings()
    PyDefaultStyleGuide.apply(settings)
    reformat("""
      class A:
          ...


      def long_function_name_with_many_parameters(argument_number_one, argument_number_two) -> int:
          ...
    """.trimIndent(), "a.py", settings)
    assertEquals("""
      class A: ...


      def long_function_name_with_many_parameters(
          argument_number_one,
          argument_number_two,
      ) -> int: ...
    """.trimIndent(), myFixture.file.text.trimEnd())
  }

  private fun assertUnchanged(text: String, configure: (PyCodeStyleSettings) -> Unit = {}) =
    assertReformatted(text, text, configure = configure)

  private fun assertReformatted(
    before: String,
    expected: String,
    fileName: String = "a.py",
    configure: (PyCodeStyleSettings) -> Unit = {},
  ) {
    val settings = CodeStyle.createTestSettings()
    settings.getCustomSettings(PyCodeStyleSettings::class.java).apply {
      ELLIPSIS_BODY_ON_SAME_LINE = true
      configure(this)
    }
    reformat(before.trimIndent(), fileName, settings)
    assertEquals(expected.trimIndent(), myFixture.file.text.trimEnd())
  }

  private fun reformat(text: String, fileName: String, settings: CodeStyleSettings) {
    myFixture.configureByText(fileName, text)
    CodeStyle.doWithTemporarySettings(myFixture.project, settings, Runnable {
      WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
        CodeStyleManager.getInstance(myFixture.project).reformat(myFixture.file)
      })
    })
  }
}
