// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.application
import org.junit.jupiter.api.Test

@TestApplication
internal class MarkdownCommentMacroTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `comment macros use HTML syntax`() {
    doTest("<!-- TODO -->")
  }

  @Test
  @RegistryKey("markdown.line.comment.link.definition.syntax.enabled", "true")
  fun `comment macros use link definition syntax`() {
    doTest("[//]: # ( TODO )")
  }

  private fun doTest(expected: String) {
    myFixture.configureByText("test.md", "<caret>")
    val template = TemplateManager.getInstance(projectFixture.get()).createTemplate(
      "comment", "test", "\$START\$ TODO \$END\$",
    ) as TemplateImpl
    template.addVariable("START", "commentStart()", "", false)
    template.addVariable("END", "commentEnd()", "", false)

    application.invokeAndWait {
      TemplateManager.getInstance(projectFixture.get()).startTemplate(myFixture.editor, template)
    }
    myFixture.checkResult(expected)
  }
}
