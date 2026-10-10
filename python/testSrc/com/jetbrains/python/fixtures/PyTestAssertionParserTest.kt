// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures

import com.intellij.idea.TestFor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.python.testAssertions.PyTestAssertion
import com.intellij.python.testAssertions.PyTestAssertionFix
import com.intellij.python.testAssertions.PyTestAssertionInliner
import com.intellij.python.testAssertions.PyTestAssertionParser
import com.intellij.python.testAssertions.PyTestAssertionParser.parseAssertions
import com.intellij.python.testAssertions.PyTestAssertionToken
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
    Assertions.assertEquals("\n\ntrailing note", assertion.comment)
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
    Assertions.assertEquals(listOf(0, 0), assertions.map { it.codeLineStart })
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
    val platformNames = HighlightSeverity.DEFAULT_SEVERITIES.map { it.name.replace(" ", "_") }
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
      x = 1 # WEAK-WARNING message
    """.trimIndent())

    Assertions.assertEquals(emptyList<Any>(), result.assertions)
    Assertions.assertEquals(listOf("1:7: Unknown directive 'WEAK-WARNING'. Did you mean 'WEAK_WARNING'?"),
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

  @Test
  fun `inliner keeps the column and the guides of indented marker lines`() = assertRoundTrip("""
    def f():
        value = make()
        #│  │   ^^^^^^ TYPE str
        #│  └ TYPE int
        #\ TYPE object
  """)

  @Test
  fun `inliner writes a multi-line payload as comment lines`() = assertRoundTrip("""
    value = make()
    #   ^^^ TYPE before line 1
    # before line 2 FIXME after line 1
    # after line 2 # trailing note
  """)

  @Test
  fun `inliner writes a multi-line payload of an indented marker line as indented comment lines`() = assertRoundTrip("""
    def f():
        value = make()
        #   ^^^ TYPE before line 1
        # before line 2
  """)

  @Test
  fun `parser reads an escaped hash as content`() {
    val code = """
      x = 1 # WARNING a \# b # note
    """.trimIndent()

    val assertion = parseAssertions(code).single()
    Assertions.assertEquals("a # b", assertion.content)
    Assertions.assertEquals("note", assertion.comment)
    assertRoundTrip(code)
  }

  @Test
  fun `parser reads FIXME only as a whole word`() {
    val assertion = parseAssertions("""
      x = 1 # WARNING Class FIXMEType and FIXME_NAME are unused
    """.trimIndent()).single()

    Assertions.assertEquals("Class FIXMEType and FIXME_NAME are unused", assertion.content)
    Assertions.assertNull(assertion.fixmeContent)
  }

  @Test
  fun `parser finds an inline assertion after a hash in a string`() {
    val code = """
      s = "#" # TYPE str
    """.trimIndent()

    val assertion = parseAssertions(code).single()
    Assertions.assertEquals("TYPE", assertion.type)
    Assertions.assertEquals("str", assertion.content)
    Assertions.assertEquals(8, assertion.assertionColumnStart)
    assertRoundTrip(code)
  }

  @Test
  fun `inliner keeps a trailing comment on its payload line`() = assertRoundTrip("""
    value = make()
    #   ^^^ TYPE a # note
    # b
  """)

  @Test
  fun `inliner keeps a trailing comment on each payload line`() = assertRoundTrip("""
    value = make()
    #   ^^^ TYPE a # first note
    # b # second note
  """)

  @Test
  fun `inliner keeps a comment line below the payload`() = assertRoundTrip("""
    value = make()
    #   ^^^ TYPE a
    # # a note below
  """)

  @Test
  fun `parser reports a marker right of the end of the code line`() {
    val result = PyTestAssertionParser.parse("""
      x = 1
      #        └ TYPE int
      #       ^^ TYPE int
      #    └ ERROR Expression expected
      #   ^^^^^^^^ ERROR Missing closing quote
    """.trimIndent())

    Assertions.assertEquals(4, result.assertions.size)
    Assertions.assertEquals(listOf("2:1: The marker is right of the end of the code line 1",
                                   "3:1: The marker is right of the end of the code line 1"),
                            result.diagnostics.map { it.toString() })
  }

  @Test
  fun `parser reports an unknown variance`() {
    val result = PyTestAssertionParser.parse("""
      x = 1 # EXPECTED_VARIANCE COVARAINT
      y = 2 # INFERRED_VARIANCE Unknown
      z = 3 # EXPECTED_VARIANCE Expected variance only available for PyReferenceExpressions
    """.trimIndent())

    Assertions.assertEquals(3, result.assertions.size)
    Assertions.assertEquals(
      listOf("1:7: Unknown value 'COVARAINT' after EXPECTED_VARIANCE. " +
             "Expected one of: COVARIANT, CONTRAVARIANT, INVARIANT, BIVARIANT, INFER_VARIANCE, NONE"),
      result.diagnostics.map { it.toString() },
    )
  }

  @Test
  fun `tokens are the markers the known directives and the modifiers`() {
    val code = """
      x = f()
      #   ^^^ WARNING TOOLTIP a FIXME b # c FIXME
      y = 1 # TYPE int
      z = 2
      #   └ TYPO int
    """.trimIndent()

    val tokens = PyTestAssertionParser.parse(code).tokens.map { code.substring(it.start, it.end) to it.kind }
    Assertions.assertEquals(listOf("^^^" to PyTestAssertionToken.Kind.MARKER,
                                   "WARNING" to PyTestAssertionToken.Kind.DIRECTIVE,
                                   "TOOLTIP" to PyTestAssertionToken.Kind.MODIFIER,
                                   "FIXME" to PyTestAssertionToken.Kind.MODIFIER,
                                   "TYPE" to PyTestAssertionToken.Kind.DIRECTIVE,
                                   "└" to PyTestAssertionToken.Kind.MARKER),
                            tokens)
  }

  @Test
  fun `tokens find FIXME on a continuation line`() {
    val code = """
      x = f()
      #   ^^^ TYPE a
      # b FIXME c
    """.trimIndent()

    val tokens = PyTestAssertionParser.parse(code).tokens.map { code.substring(it.start, it.end) to it.kind }
    Assertions.assertEquals(listOf("^^^" to PyTestAssertionToken.Kind.MARKER,
                                   "TYPE" to PyTestAssertionToken.Kind.DIRECTIVE,
                                   "FIXME" to PyTestAssertionToken.Kind.MODIFIER),
                            tokens)
  }

  @Test
  fun `marker below a multi-line assertion points at the code line`() {
    val code = """
      x = f(a)
      #   │ └ TYPE A
      # B
      #   └ TYPE int
    """.trimIndent()

    val assertions = parseAssertions(code)
    Assertions.assertEquals(listOf("A\nB", "int"), assertions.map { it.content })
    Assertions.assertEquals(listOf(0, 0), assertions.map { it.codeLineStart })
    assertRoundTrip(code)
  }

  @Test
  fun `payload line that starts with a hash continues the payload`() {
    val code = """
      value = make()
      #   ^^^ TYPE a
      # \#x
      # ^ 1
    """.trimIndent()

    Assertions.assertEquals("a\n#x\n^ 1", parseAssertions(code).single().content)
    assertRoundTrip(code)
  }

  @Test
  fun `empty payload line stays in the content`() {
    val code = """
      value = make()
      #   ^^^ TYPE a
      #
      # b
    """.trimIndent()

    Assertions.assertEquals("a\n\nb", parseAssertions(code).single().content)
    assertRoundTrip(code)
  }

  @Test
  fun `inliner writes a multi-line inline assertion above the marker lines of its code line`() = assertRoundTrip("""
    x = f() # TYPE a
            # b
    #   ^^^ WARNING w
  """)

  @Test
  fun `inliner writes a new highlight on a line whose inline assertion has no counterpart`() {
    val text = "x = f() # TYPE int"
    val warning = PyTestAssertion(codeOffsetStart = 4, codeLineStart = 0, codeColumnStart = 4, codeColumnEnd = 7,
                                  type = "WARNING", content = "w")

    val actualText = PyTestAssertionInliner.generateActualText(text, parseAssertions(text), listOf(warning))

    Assertions.assertEquals("x = f()\n#   ^^^ WARNING w", actualText)
  }

  @Test
  fun `parser reports a span that starts with a left marker`() {
    val result = PyTestAssertionParser.parse("""
      foo = 1
      #\^ WARNING w
    """.trimIndent())

    Assertions.assertEquals(listOf("2:1: A span cannot start with '\\'. Start the span in the column after the '#'"),
                            result.diagnostics.map { it.toString() })
  }

  @Test
  fun `parser reports a marker without a directive`() {
    val result = PyTestAssertionParser.parse("""
      foo = 1
      #   ^^^
      bar = 2
      #   ^^^ 123
    """.trimIndent())

    Assertions.assertEquals(emptyList<Any>(), result.assertions)
    Assertions.assertEquals(listOf("2:1: The marker has no directive", "4:1: The marker has no directive"),
                            result.diagnostics.map { it.toString() })
  }

  @Test
  fun `diagnostic of a near miss holds the name and its fix`() {
    val code = "x = 1 # WEAK-WARNING message"

    val diagnostic = PyTestAssertionParser.parse(code).diagnostics.single()

    Assertions.assertEquals("WEAK-WARNING", code.substring(diagnostic.start, diagnostic.end))
    Assertions.assertEquals(PyTestAssertionFix.Kind.DIRECTIVE_NAME, diagnostic.fix?.kind)
    Assertions.assertEquals("x = 1 # WEAK_WARNING message", diagnostic.fix?.applyTo(code))
  }

  @Test
  fun `parser reports a one-column span and fixes it to a corner marker`() = assertCanonicalForm("""
    x = 1
    #   ^ TYPE int
  """, """
    x = 1
    #   └ TYPE int
  """)

  @Test
  fun `parser reports an ascii guide and two spaces after a directive`() = assertCanonicalForm("""
    x = f(a)
    #   | └ TYPE A
    #   └ TYPE  int
  """, """
    x = f(a)
    #   │ └ TYPE A
    #   └ TYPE int
  """)

  @Test
  fun `parser reports marker lines that start with the leftmost marker`() = assertCanonicalForm("""
    x = f(a)
    #   └ TYPE int
    #     └ TYPE A
  """, """
    x = f(a)
    #   │ └ TYPE A
    #   └ TYPE int
  """)

  @Test
  fun `parser reports an inline assertion without a space after the hash`() = assertCanonicalForm("""
    x = 1 #TYPE int
  """, """
    x = 1 # TYPE int
  """)

  private fun assertCanonicalForm(code: String, canonicalCode: String) {
    val text = code.trimIndent()
    val diagnostic = PyTestAssertionParser.parse(text).diagnostics.single()
    Assertions.assertTrue(diagnostic.message.contains("canonical form"), diagnostic.message)
    Assertions.assertEquals(canonicalCode.trimIndent(), diagnostic.fix?.applyTo(text))
  }

  private fun assertRoundTrip(code: String) {
    val text = code.trimIndent()
    val result = PyTestAssertionParser.parse(text)
    Assertions.assertEquals(emptyList<Any>(), result.diagnostics)
    Assertions.assertEquals(text, PyTestAssertionInliner.generateActualText(text, result.assertions, result.assertions))
  }
}
