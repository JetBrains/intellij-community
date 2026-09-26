// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.fstrings.PyFStringFormatSpecHoverDocumentationProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for f-string format specification hover documentation.
 *
 * Hovering anywhere within a format spec describes the whole specification, not the single character
 * under the caret.
 */
@Layers.Functional
@Subsystems.CodeInsight
@TestFor(classes = [PyFStringFormatSpecHoverDocumentationProvider::class], issues = ["PY-88389"])
class PyFStringFormatSpecHoverDocumentationTest : PyCodeInsightTestCase() {

  @Test
  fun `hover shows full format spec for simple type`() =
    doTest("f'{1:<caret>d}'", "Format Specification", "Type", "Decimal")

  @Test
  fun `hover shows full format spec with precision and type`() =
    doTest("f'{3.14:<caret>.2f}'", "Format Specification", "Precision", "Type", "Fixed-point")

  @Test
  fun `hover shows full format spec with alignment and width`() =
    doTest("f'{1:<caret><10d}'", "Format Specification", "Alignment", "Left-align", "Width", "10", "Type", "Decimal")

  @Test
  fun `hover shows full format spec with fill and alignment`() =
    doTest("f'{1:0<caret>>10d}'", "Format Specification", "Fill", "Alignment", "Right-align", "Width")

  @Test
  fun `hover shows full format spec with sign`() =
    doTest("f'{42:<caret>+d}'", "Format Specification", "Sign", "Type", "Decimal")

  @Test
  fun `hover shows full format spec with grouping`() =
    doTest("f'{1000000:<caret>,d}'", "Format Specification", "Grouping", "Comma separator", "Type")

  @Test
  fun `hover shows full format spec with alternate form`() =
    doTest("f'{255:<caret>#x}'", "Format Specification", "Alternate", "Type", "Hex")

  @Test
  fun `hover shows datetime format spec`() =
    doTest("f'{dt:<caret>%Y-%m-%d}'", "Format Specification", "Datetime", "Year", "Month", "Day")

  @Test
  fun `no hover outside format spec`() = doTestNoDoc("f'{1<caret>:d}'")

  @Test
  fun `no hover inside nested replacement field`() =
    // The caret is on the nested `width` expression, which has its own documentation.
    doTestNoDoc("f'{x:{wid<caret>th}}'")

  @Test
  fun `hover treats percentage type as type not datetime`() {
    // '%' as a presentation type (e.g. .2%) must not be misread as a datetime directive.
    val html = computeFormatSpecHtml("f'{x:<caret>.2%}'")
    assertTrue("Percentage" in html, "Expected percentage presentation type, got: $html")
    assertFalse("Datetime format string" in html, "Percentage type must not be rendered as a datetime spec, got: $html")
  }

  @Test
  fun `hover escapes html metacharacters in format spec`() {
    // Fill characters can be HTML metacharacters ('&', '<', '>'); they must be escaped, not rendered as markup.
    val html = computeFormatSpecHtml("f'{x:<caret>&>10}'")
    assertTrue("<code>&amp;&gt;10</code>" in html, "Expected the format spec to be HTML-escaped, got: $html")
  }

  @Test
  fun `hover for dot comma`() =
    doTest("f'{1:<caret>.,f}'", "Format Specification", "Precision", "Grouping", "Type", "Fixed-point")

  @Test
  fun `hover omits the example for a huge precision`() {
    // The example is rendered eagerly, so a precision beyond the bound gets no example at all.
    val html = computeFormatSpecHtml("f'{x:<caret>.999999999f}'")
    assertFalse("Example" in html, "Expected no example for a huge precision, got: $html")
  }

  @Test
  fun `hover omits the example for a huge width`() {
    val html = computeFormatSpecHtml("f'{x:<caret>999999999}'")
    assertFalse("Example" in html, "Expected no example for a huge width, got: $html")
  }

  @Test
  fun `full html output for complex format spec`() {
    val html = computeFormatSpecHtml("f'{value:<caret>*>+10,.2f}'")

    // The documentation uses the platform's sections table, so the popup styles it like every other
    // quick documentation. The spec itself is escaped: the '>' must not become markup.
    assertTrue("<table class=\"sections\">" in html, "Expected the platform sections table, got: $html")
    assertTrue("Format Specification: <code>*&gt;+10,.2f</code>" in html, "Expected the escaped spec header, got: $html")

    for (row in listOf(
      "Fill" to "<code>*</code> — Character used for padding",
      "Alignment" to "<code>&gt;</code> — Right-align",
      "Sign" to "<code>+</code> — Show sign",
      "Width" to "<code>10</code> — Minimum field width",
      "Grouping" to "<code>,</code> — Comma separator",
      "Precision" to "<code>.2</code> — Digits after decimal point",
      "Type" to "<code>f</code> — Fixed-point",
    )) {
      val (component, content) = row
      assertTrue(component in html, "Expected the $component row, got: $html")
      assertTrue(content in html, "Expected the $component content '$content', got: $html")
    }
  }

  @Test
  fun `full html output for datetime format spec`() {
    val html = computeFormatSpecHtml("f'{dt:<caret>%Y-%m-%d %H:%M:%S}'")

    assertTrue("Datetime format string" in html, "Expected the datetime label, got: $html")
    for (directive in listOf("%Y", "%m", "%d", "%H", "%M", "%S")) {
      assertTrue("<code>$directive</code>" in html, "Expected the $directive row, got: $html")
    }
  }

  @Test
  fun `no style block in the documentation`() =
    // A raw stylesheet would leak its global selectors into the rest of the popup and ignore the theme.
    doTestNoMarkup("f'{value:<caret>*>+10,.2f}'", "<style>")

  private fun computeFormatSpecHtml(code: String): String {
    var html = ""
    runInEdtAndWait {
      myFixture.configureByText("test.py", code)
      val targets = IdeDocumentationTargetProvider.getInstance(myFixture.project)
        .documentationTargets(myFixture.editor, myFixture.file, myFixture.caretOffset)
      assertFalse(targets.isEmpty(), "Expected documentation targets but got none")

      val data = computeDocumentationBlocking(targets.first().createPointer())
      assertNotNull(data, "Expected documentation data but got null")
      html = data!!.html
    }
    return html
  }

  private fun doTest(code: String, vararg expectedContains: String) {
    val html = computeFormatSpecHtml(code)
    for (expected in expectedContains) {
      assertTrue(expected in html, "Expected documentation to contain '$expected' but got: $html")
    }
  }

  private fun doTestNoMarkup(code: String, vararg unexpected: String) {
    val html = computeFormatSpecHtml(code)
    for (markup in unexpected) {
      assertFalse(markup in html, "Expected no '$markup' in the documentation, got: $html")
    }
  }

  private fun doTestNoDoc(code: String) = runInEdtAndWait {
    myFixture.configureByText("test.py", code)
    val targets = IdeDocumentationTargetProvider.getInstance(myFixture.project)
      .documentationTargets(myFixture.editor, myFixture.file, myFixture.caretOffset)

    // Filter to only our format spec targets
    val formatSpecTargets = targets.filter { "PyFormatSpecDocumentationTarget" in it.javaClass.name }
    assertTrue(formatSpecTargets.isEmpty(), "Expected no format spec documentation targets, got: $formatSpecTargets")
  }
}
