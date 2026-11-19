// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateContextTypes
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.lang.MarkdownContextType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownLiveTemplateContextTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `test markdown context is applicable only in markdown files`(): Unit = timeoutRunBlocking {
    val template = createMarkdownTemplate()

    myFixture.configureByText("test.md", "Some <caret>text")
    readAction {
      assertTrue(TemplateManagerImpl.isApplicable(template, TemplateActionContext.expanding(myFixture.file, myFixture.editor)))
    }

    myFixture.configureByText("test.txt", "Some <caret>text")
    readAction {
      assertFalse(TemplateManagerImpl.isApplicable(template, TemplateActionContext.expanding(myFixture.file, myFixture.editor)))
    }
  }

  private fun createMarkdownTemplate(): TemplateImpl {
    val template = TemplateManager.getInstance(myFixture.project).createTemplate("md", "Markdown", "text") as TemplateImpl
    template.templateContext.setEnabled(TemplateContextTypes.getByClass(MarkdownContextType::class.java), true)
    CodeInsightTestUtil.addTemplate(template, disposable)
    return template
  }
}
