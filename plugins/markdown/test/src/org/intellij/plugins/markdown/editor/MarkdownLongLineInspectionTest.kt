// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.application.options.CodeStyle
import com.intellij.codeInspection.longLine.LongLineInspection
import com.intellij.lang.LangBundle
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.junit.Test

class MarkdownLongLineInspectionTest: LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `tables and long URLs are ignored while wrappable content is reported`() {
    val rightMargin = 20
    val description = LangBundle.message("inspection.message.line.longer.than.allowed.by.code.style.columns", rightMargin)
    val content = """
      | long table heading | another heading |
      |--------------------|-----------------|
      | long table content | another content |

      [inline](https://example.com/a/very/long/path)
      [reference]: https://example.com/a/very/long/path
      https://example.com/a/very/long/path
      <https://example.com/a/very/long/path>

      abcdefghijklmnopqrs <warning descr="$description">[short](url)</warning>
      abcdefghijklmnopqrst<warning descr="$description">uvwxyz</warning>
    """.trimIndent()

    CodeStyle.doWithTemporarySettings(project, CodeStyle.getSettings(project)) { settings ->
      settings.setRightMargin(MarkdownLanguage.INSTANCE, rightMargin)
      myFixture.enableInspections(LongLineInspection())
      myFixture.configureByText("test.md", content)
      myFixture.checkHighlighting()
    }
  }
}
