package com.jetbrains.python.markdown

import com.jetbrains.python.fixtures.PyTestCase

class PyCodeFenceTest : PyTestCase() {
  fun testPythonInjected() {
    myFixture.configureByText("a.md", """
      ```python
      foo = 1
      f<caret>

      ```
    """.trimIndent())
    myFixture.completeBasic()
    assertContainsElements(lookupStrings, "foo")
  }

  fun testPyInjected() {
    myFixture.configureByText("a.md", """
      ```py
      foo = 1
      f<caret>

      ```
    """.trimIndent())
    myFixture.completeBasic()
    assertContainsElements(lookupStrings, "foo")
  }

  fun testPyConInjected() {
    myFixture.configureByText("a.md", """
      ```pycon
      >>> foo = 1
      >>> f<caret>

      ```
    """.trimIndent())
    myFixture.completeBasic()
    assertContainsElements(lookupStrings, "foo")
  }

  fun testPyConNoCompletionForUndefinedName() {
    myFixture.configureByText("a.md", """
      ```pycon
      >>> print(foo)
      >>> f<caret>

      ```
    """.trimIndent())
    myFixture.completeBasic()
    assertDoesntContain(lookupStrings, "foo")
  }

  fun testPythonReplInjected() {
    myFixture.configureByText("a.md", """
      ```python-repl
      >>> foo = 1
      >>> f<caret>

      ```
    """.trimIndent())
    myFixture.completeBasic()
    assertContainsElements(lookupStrings, "foo")
  }

  private val lookupStrings: List<String>
    get() = myFixture.lookupElementStrings ?: emptyList()
}
