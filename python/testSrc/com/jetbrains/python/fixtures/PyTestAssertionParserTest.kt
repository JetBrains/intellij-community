// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures

import com.intellij.idea.TestFor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.python.testAssertions.PyTestAssertionParser
import com.intellij.python.testAssertions.PyTestAssertionParser.parseAssertions
import com.intellij.python.testAssertions.PyTestDirectiveKind
import com.intellij.python.testAssertions.PyTestDirectives
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Subsystems.CodeInsight
@Layers.Functional
@TestFor(classes = [PyTestAssertionParser::class, PyTestDirectives::class])
class PyTestAssertionParserTest {

  @Test
  fun `parser parses marker span with multiline payload fixme and trailing comment`() {
    val code = """
      value = make()
      #   ^^^ TYPE before line 1
      # before line 2 FIXME after line 1
      # after line 2 # trailing note
    """.trimIndent()

    val assertion = parseAssertions(code).single()
    Assertions.assertEquals(0, assertion.codeLineStart)
    Assertions.assertEquals(4, assertion.codeColumnStart)
    Assertions.assertEquals(7, assertion.codeColumnEnd)
    Assertions.assertEquals(1, assertion.assertionLineStart)
    Assertions.assertEquals(3, assertion.assertionLineEnd)
    Assertions.assertEquals("TYPE", assertion.type)
    Assertions.assertEquals("before line 1\nbefore line 2", assertion.content)
    Assertions.assertEquals("after line 1\nafter line 2", assertion.fixmeContent)
    Assertions.assertEquals("trailing note", assertion.comment)
  }

  @Test
  fun `parser ignores inline comments with unknown assertion type`() {
    val code = """
      x = 1 # TODO plain comment
      y = 2 # TYPE int
    """.trimIndent()

    val assertions = parseAssertions(code)
    Assertions.assertEquals(1, assertions.size)
    val assertion = assertions.single()
    Assertions.assertEquals(1, assertion.codeLineStart)
    Assertions.assertEquals(-1, assertion.codeColumnStart)
    Assertions.assertEquals("TYPE", assertion.type)
    Assertions.assertEquals("int", assertion.content)
  }

  @Test
  fun `parser resolves left marker to column zero`() {
    val code = """
      n = 1
      #\ TYPE int
    """.trimIndent()

    val assertion = parseAssertions(code).single()
    Assertions.assertEquals(0, assertion.codeColumnStart)
    Assertions.assertEquals(-1, assertion.codeColumnEnd)
    Assertions.assertEquals("TYPE", assertion.type)
    Assertions.assertEquals("int", assertion.content)
  }

  @Test
  fun `parser stops payload collection at next assertion start`() {
    val code = """
      value = 1
      #   └ TYPE first line
      # continuation
      #   └ TYPE second line
    """.trimIndent()

    val assertions = parseAssertions(code)
    Assertions.assertEquals(2, assertions.size)
    Assertions.assertEquals("first line\ncontinuation", assertions[0].content)
    Assertions.assertEquals("second line", assertions[1].content)
  }

  @Test
  fun `parser stops payload collection at differently aligned comment`() {
    val code = """
      value = call()
      #       ^^^^^^ WARNING first line
      # second line
          # Not assertion
      # Also not assertion
    """.trimIndent()

    val assertion = parseAssertions(code).single()
    Assertions.assertEquals("first line\nsecond line", assertion.content)
  }

  @Test
  fun `parser resolves stacked markers under a type comment to the type comment line`() {
    val code = """
      def f(x):
          # type: (int) -> str
      #           ^^^^^ WARNING first
      #                    ^^^ WARNING second
          pass
    """.trimIndent()

    val assertions = parseAssertions(code)
    Assertions.assertEquals(2, assertions.size)
    Assertions.assertTrue(assertions.all { it.codeLineStart == 1 })
    Assertions.assertEquals(listOf("first", "second"), assertions.map { it.content })
  }

  @Test
  fun `masking blanks a marker before a function type comment so it still types the function`() {
    val code = """
      def f(a: int) -> int:
      #   ^ WARNING both
          # type: (int) -> int
          pass
    """.trimIndent()

    val masked = PyTestAssertionParser.maskAssertions(code, parseAssertions(code))

    Assertions.assertEquals(code.length, masked.length) // offsets preserved
    Assertions.assertFalse(masked.contains("WARNING")) // marker blanked
    Assertions.assertTrue(masked.contains("# type: (int) -> int")) // type comment intact
  }

  @Test
  fun `parser accepts FIXME with empty content and trailing comment`() {
    val code = """
      value = 1
      #   └ WARNING Expected type 'A[int]', got 'B[object]' instead FIXME # PY-89564
    """.trimIndent()

    val assertion = parseAssertions(code).single()
    Assertions.assertEquals("WARNING", assertion.type)
    Assertions.assertEquals("Expected type 'A[int]', got 'B[object]' instead", assertion.content)
    Assertions.assertNotNull(assertion.fixmeContent)
    Assertions.assertTrue(assertion.fixmeContent!!.isEmpty())
    Assertions.assertEquals("PY-89564", assertion.comment)
  }

  @Test
  fun `parser accepts inline FIXME with empty content and trailing comment`() {
    val code = """
      value = 1 # WARNING superfluous warning FIXME # PY-89564
    """.trimIndent()

    val assertion = parseAssertions(code).single()
    Assertions.assertEquals("WARNING", assertion.type)
    Assertions.assertEquals("superfluous warning", assertion.content)
    Assertions.assertNotNull(assertion.fixmeContent)
    Assertions.assertTrue(assertion.fixmeContent!!.isEmpty())
    Assertions.assertEquals("PY-89564", assertion.comment)
  }

  @Test
  fun `parser accepts FIXME with empty content without trailing comment`() {
    val code = """
      value = 1
      #   └ WARNING superfluous warning FIXME
    """.trimIndent()

    val assertion = parseAssertions(code).single()
    Assertions.assertEquals("WARNING", assertion.type)
    Assertions.assertEquals("superfluous warning", assertion.content)
    Assertions.assertNotNull(assertion.fixmeContent)
    Assertions.assertTrue(assertion.fixmeContent!!.isEmpty())
    Assertions.assertNull(assertion.comment)
  }

  @Test
  fun `severity directives are the default highlight severities`() {
    val platformNames = HighlightSeverity.DEFAULT_SEVERITIES.map { it.name.replace(" ", "-") }
    Assertions.assertEquals(platformNames.toSet(), PyTestDirectives.severityNames)
  }

  @Test
  fun `each directive has a unique name and a description`() {
    val names = PyTestDirectives.all.map { it.name }
    Assertions.assertEquals(names.distinct(), names)
    for (directive in PyTestDirectives.all) {
      Assertions.assertTrue(directive.description.isNotBlank(), "No description for ${directive.name}")
    }
  }

  @Test
  fun `modifiers are not assertions`() {
    Assertions.assertEquals(PyTestDirectiveKind.MODIFIER, PyTestDirectives.FIXME.kind)
    Assertions.assertNull(PyTestDirectives.findAssertion("FIXME"))
    Assertions.assertNull(PyTestDirectives.findAssertion("TOOLTIP"))
  }

  @Test
  fun `parser treats a lower case inline word as a plain comment`() {
    val result = PyTestAssertionParser.parse("""
      x = 1 # type : ignore
      y = 2 # error on purpose
    """.trimIndent())

    Assertions.assertEquals(emptyList<Any>(), result.assertions)
    Assertions.assertEquals(emptyList<Any>(), result.diagnostics)
  }

  @Test
  fun `parser treats an unknown upper case inline word as a plain comment`() {
    val result = PyTestAssertionParser.parse("""
      x = 1 # TODO plain comment
      y = 2 # TYPE_CHECKING is a name
    """.trimIndent())

    Assertions.assertEquals(emptyList<Any>(), result.assertions)
    Assertions.assertEquals(emptyList<Any>(), result.diagnostics)
  }

  @Test
  fun `parser reports a near miss of an inline directive`() {
    val result = PyTestAssertionParser.parse("""
      x = 1 # WEAK_WARNING message
    """.trimIndent())

    Assertions.assertEquals(emptyList<Any>(), result.assertions)
    Assertions.assertEquals(listOf("1:7: Unknown directive 'WEAK_WARNING'. Did you mean 'WEAK-WARNING'?"),
                            result.diagnostics.map { it.toString() })
  }

  @Test
  fun `parser reports an unknown directive after a marker`() {
    val result = PyTestAssertionParser.parse("""
      x = 1
      #   └ TYPO int
      #   └ EXPECTED-TYPE int
    """.trimIndent())

    Assertions.assertEquals(listOf("TYPO", "EXPECTED-TYPE"), result.assertions.map { it.type })
    Assertions.assertEquals(listOf("2:1: Unknown directive 'TYPO'",
                                   "3:1: Unknown directive 'EXPECTED-TYPE'. Did you mean 'EXPECTED_TYPE'?"),
                            result.diagnostics.map { it.toString() })
  }

  @Test
  fun `parser reports a bad payload`() {
    val result = PyTestAssertionParser.parse("""
      x = 1 # ISSUES all
      y = 2 # IS_BUILTIN TRUE
      z = 3
      #   └ CARET here
    """.trimIndent())

    Assertions.assertEquals(3, result.assertions.size)
    Assertions.assertEquals(listOf("1:7: Expected * after ISSUES",
                                   "2:7: Expected nothing or FALSE after IS_BUILTIN",
                                   "4:1: Expected nothing after CARET"),
                            result.diagnostics.map { it.toString() })
  }

  @Test
  fun `parser accepts a good payload`() {
    val result = PyTestAssertionParser.parse("""
      x = 1 # ISSUES *
      y = 2 # IS_BUILTIN FALSE
      z = 3
      #   └ CARET
    """.trimIndent())

    Assertions.assertEquals(3, result.assertions.size)
    Assertions.assertEquals(emptyList<Any>(), result.diagnostics)
  }

  @Test
  fun `parser reports a marker without a code line`() {
    val result = PyTestAssertionParser.parse("""
      #   └ TYPE int
      x = 1
    """.trimIndent())

    Assertions.assertEquals(emptyList<Any>(), result.assertions)
    Assertions.assertEquals(listOf("1:1: The marker has no code line above it"), result.diagnostics.map { it.toString() })
  }
}
