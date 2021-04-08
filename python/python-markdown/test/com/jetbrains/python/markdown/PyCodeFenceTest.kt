package com.jetbrains.python.markdown

import com.jetbrains.python.fixtures.PyTestCase

/**
 * Tests for [PyCodeFenceLanguageProvider].
 */
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

  fun testPyConTypeEnter() {
    myFixture.configureByText("a.md", """
      ```pycon
      >>> "Hello, World!"<caret>
      'Hello, World!'

      ```
    """.trimIndent())
    myFixture.type("\n")
    myFixture.checkResult("""
      ```pycon
      >>> "Hello, World!"
      <caret>
      'Hello, World!'

      ```
    """.trimIndent())
  }

  fun testPyConReformat() {
    myFixture.configureByText("a.md", """
      ```pycon
      >>> 1
      >>> 2
      
      ```
    """.trimIndent())
    reformatFile()
    myFixture.checkResult("""
      ```pycon
      >>> 1
      >>> 2
      
      ```
    """.trimIndent())
  }

  fun testDoctestInjected() {
    myFixture.configureByText("a.md", """
      ```doctest
      foo = 1
      f<caret>

      ```
    """.trimIndent())
    myFixture.completeBasic()
    assertContainsElements(lookupStrings, "foo")
  }

  fun testDefaultPythonDialectsInCompletion() {
    myFixture.configureByText("a.md", """
      ```<caret>
      foo = 1
      f

      ```
    """.trimIndent())
    myFixture.completeBasic()
    assertContainsElements(lookupStrings, "doctest", "python", "pycon")
  }

  private val lookupStrings: List<String>
    get() = myFixture.lookupElementStrings ?: emptyList()
}
