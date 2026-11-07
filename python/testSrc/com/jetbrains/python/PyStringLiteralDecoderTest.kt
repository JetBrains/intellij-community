// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.PyStringLiteralDecoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestFor(classes = [PyStringLiteralDecoder::class], issues = ["PY-93852"])
@Subsystems.CodeInsight
@Layers.Functional
class PyStringLiteralDecoderTest : PyCodeInsightTestCase() {

  @Test
  fun `line continuation decodes to nothing`() = runInEdtAndWait {
    assertEquals("ab", valueOf("'a\\\nb'"))
    assertEquals("ab", valueOf("'''a\\\nb'''"))
    assertEquals("ab", valueOf("b'a\\\nb'"))
    assertEquals("ab", valueOf("f'a\\\nb'"))
  }

  @Test
  fun `line continuation stays in a raw literal`() = runInEdtAndWait {
    assertEquals("a\\\nb", valueOf("r'a\\\nb'"))
  }

  @Test
  fun `bell escape decodes to the bell character`() = runInEdtAndWait {
    assertEquals(Char(7).toString(), valueOf("'\\a'"))
  }

  @Test
  fun `named escape decodes to the named character`() = runInEdtAndWait {
    assertEquals("•", valueOf("'\\N{BULLET}'"))
    assertEquals("•", valueOf("'\\N{bullet}'"))
    assertEquals("a•b", valueOf("'a\\N{BULLET}b'"))
    assertEquals("•", valueOf("f'\\N{BULLET}'"))
    assertEquals(String(Character.toChars(0x1F600)), valueOf("'\\N{GRINNING FACE}'"))
  }

  @Test
  fun `named escape of a name that Python rejects keeps the escape text`() = runInEdtAndWait {
    assertEquals("\\N{ BULLET}", valueOf("'\\N{ BULLET}'"))
    assertEquals("\\N{NO SUCH CHARACTER}", valueOf("'\\N{NO SUCH CHARACTER}'"))
    assertEquals("\\N{PRIVATE USE AREA E000}", valueOf("'\\N{PRIVATE USE AREA E000}'"))
    assertEquals("\\N{LATıN SMALL LETTER A}", valueOf("'\\N{LATıN SMALL LETTER A}'"))
    assertEquals("\\N{LINE FEED (LF)}", valueOf("'\\N{LINE FEED (LF)}'"))
  }

  @Test
  fun `named escape stays in bytes and raw literals`() = runInEdtAndWait {
    assertEquals("\\N{BULLET}", valueOf("b'\\N{BULLET}'"))
    assertEquals("\\N{BULLET}", valueOf("r'\\N{BULLET}'"))
  }

  @Test
  fun `named escape in Python 2 decodes only in a unicode literal`() = runInEdtAndWait {
    assertEquals("\\N{BULLET}", valueOf("'\\N{BULLET}'", LanguageLevel.PYTHON27))
    assertEquals("•", valueOf("u'\\N{BULLET}'", LanguageLevel.PYTHON27))
  }

  @Test
  fun `code point escapes stay in bytes literals of Python 3`() = runInEdtAndWait {
    assertEquals("\\u0041", valueOf("b'\\u0041'"))
    assertEquals("\\u0041", valueOf("rb'\\u0041'"))
  }

  @Test
  fun `code point escapes decode in a raw literal with unicode literals of Python 2`() = runInEdtAndWait {
    val text = "from __future__ import unicode_literals\nexpr = r'\\u0041'"
    assertEquals("A", literal(text, LanguageLevel.PYTHON27).stringValue)
  }

  @Test
  fun `escaper skips a line continuation`() = runInEdtAndWait {
    val expression = literal("expr = 'a\\\nbc'", LanguageLevel.getLatest())
    val escaper = expression.createLiteralTextEscaper()
    val content = TextRange(1, 6)
    assertEquals("abc", StringBuilder().also { escaper.decode(content, it) }.toString())
    assertEquals("bc", StringBuilder().also { escaper.decode(TextRange(3, 6), it) }.toString())
    assertEquals(1, escaper.getOffsetInHost(0, content))
    assertEquals(4, escaper.getOffsetInHost(1, content))
    assertEquals(5, escaper.getOffsetInHost(2, content))
    assertEquals(6, escaper.getOffsetInHost(3, content))
  }

  private fun valueOf(literal: String, level: LanguageLevel = LanguageLevel.getLatest()): String =
    literal("expr = $literal", level).stringValue

  private fun literal(text: String, level: LanguageLevel): PyStringLiteralExpression {
    val file = PyElementGenerator.getInstance(myFixture.project).createDummyFile(level, text)
    return PsiTreeUtil.findChildrenOfType(file, PyStringLiteralExpression::class.java).last()
  }
}
