// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateContextTypes
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.intellij.plugins.markdown.lang.MarkdownContextType

class MarkdownLiveTemplateContextTest : LightPlatformCodeInsightTestCase() {
  fun `test markdown context is applicable only in markdown files`() {
    val template = createMarkdownTemplate()

    configureFromFileText("test.md", "Some <caret>text")
    assertTrue(TemplateManagerImpl.isApplicable(template, TemplateActionContext.expanding(file, editor)))

    configureFromFileText("test.txt", "Some <caret>text")
    assertFalse(TemplateManagerImpl.isApplicable(template, TemplateActionContext.expanding(file, editor)))
  }

  private fun createMarkdownTemplate(): TemplateImpl {
    val template = TemplateManager.getInstance(project).createTemplate("md", "Markdown", "text") as TemplateImpl
    template.templateContext.setEnabled(TemplateContextTypes.getByClass(MarkdownContextType::class.java), true)
    CodeInsightTestUtil.addTemplate(template, testRootDisposable)
    return template
  }
}
