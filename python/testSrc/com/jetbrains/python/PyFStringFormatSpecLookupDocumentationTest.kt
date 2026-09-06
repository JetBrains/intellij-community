// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.fstrings.PyFStringFormatSpecDocumentationProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/**
 * Tests the documentation shown next to a format-spec completion item.
 *
 * The item carries its catalog option, so the provider describes the option itself rather than parsing
 * the item text again.
 */
@Layers.Functional
@Subsystems.CodeInsight
@TestFor(classes = [PyFStringFormatSpecDocumentationProvider::class], issues = ["PY-88388", "PY-88389"])
class PyFStringFormatSpecLookupDocumentationTest : PyCodeInsightTestCase() {

  @Test
  fun `documentation of a presentation type item`() {
    val html = documentationFor("f'{42:<caret>}'", "d")
    assertTrue("Decimal" in html, "Expected the short description, got: $html")
    assertTrue("Outputs the number in base 10" in html, "Expected the full description, got: $html")
    assertTrue("Presentation Type" in html, "Expected the category label, got: $html")
  }

  @Test
  fun `documentation carries the canonical example`() {
    val html = documentationFor("f'{42:<caret>}'", "d")
    assertTrue("Example" in html, "Expected an example section, got: $html")
    assertTrue("{42:d}" in html, "Expected the catalog example, got: $html")
  }

  @Test
  fun `documentation of an alignment item escapes the spec`() {
    // '<' is both an alignment operator and an HTML metacharacter.
    val html = documentationFor("f'{42:<caret>}'", "<")
    assertTrue("&lt;" in html, "Expected the spec to be escaped, got: $html")
    assertTrue("Left-align" in html, "Expected the short description, got: $html")
  }

  @Test
  fun `documentation of a datetime directive item`() {
    val html = documentationFor("""
      from datetime import datetime
      def test(dt: datetime):
          f"{dt:<caret>}"
    """.trimIndent(), "%Y")
    assertTrue("Year" in html, "Expected the short description, got: $html")
    assertTrue("DateTime Format" in html, "Expected the category label, got: $html")
  }

  @Test
  fun `no documentation for an unrelated lookup item`() = runInEdtAndWait {
    // A plain reference item carries no format-spec option, so this provider must not answer for it.
    // Two candidates keep the lookup open; a single match would be inserted straight away.
    myFixture.configureByText("test.py", """
      widths = 1
      widget = 2
      wid<caret>
    """.trimIndent())
    val item = myFixture.completeBasic()?.firstOrNull { it.lookupString == "widths" } ?: fail("No 'widths' item")
    val targets = IdeDocumentationTargetProvider.getInstance(myFixture.project)
      .documentationTargets(myFixture.editor, myFixture.file, item)
    assertTrue(targets.none { "PyFormatSpecOptionDocumentationTarget" in it.javaClass.name },
               "Expected no format-spec documentation target, got: $targets")
  }

  private fun documentationFor(code: String, lookupString: String): String {
    var html = ""
    runInEdtAndWait {
      myFixture.configureByText("test.py", code)
      val items = myFixture.completeBasic() ?: fail("Completion produced no lookup items")
      val item = items.firstOrNull { it.lookupString == lookupString }
                 ?: fail("No '$lookupString' item among ${items.map { it.lookupString }}")

      val targets = IdeDocumentationTargetProvider.getInstance(myFixture.project)
        .documentationTargets(myFixture.editor, myFixture.file, item)
      val target = targets.firstOrNull { "PyFormatSpecOptionDocumentationTarget" in it.javaClass.name }
                   ?: fail("No format-spec documentation target for '$lookupString', got: $targets")

      val data = computeDocumentationBlocking(target.createPointer())
      assertNotNull(data, "Expected documentation data but got null")
      html = data!!.html
    }
    return html
  }
}
