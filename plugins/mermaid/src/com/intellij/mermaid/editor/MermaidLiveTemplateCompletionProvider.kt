// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.editor

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project

abstract class MermaidLiveTemplateCompletionProvider :
  CompletionProvider<CompletionParameters>() {
  private val priority = 10.0

  protected fun createKeywordLookupElement(project: Project, keyword: String, predefinedNameVar: String? = null): LookupElement {
    val templateManager = TemplateManager.getInstance(project) as TemplateManagerImpl
    val template = TemplateSettings.getInstance().getTemplateById("mermaid_$keyword")
    val lookupString = predefinedNameVar ?: keyword

    val indentOptions = CodeStyle.getSettings(project).getLanguageIndentOptions(MermaidLanguage)

    val predefinedVarValues = buildMap {
      put("INDENT", " ".repeat(indentOptions.INDENT_SIZE))
      if (predefinedNameVar != null) put("NAME", predefinedNameVar)
    }
    val insertHandler = createTemplateBasedInsertHandler(templateManager, template, predefinedVarValues)

    return PrioritizedLookupElement.withPriority(
      LookupElementBuilder
        .create(lookupString)
        .withCaseSensitivity(false)
        .withBoldness(true)
        .withInsertHandler(insertHandler), priority
    )
  }

  private fun createTemplateBasedInsertHandler(
    templateManager: TemplateManagerImpl,
    template: Template?,
    predefinedVarValues: Map<String, String>
  ): InsertHandler<LookupElement> {
    return InsertHandler { context: InsertionContext, _: LookupElement? ->
      val editor = context.editor
      if (template != null) {
        val startOffset = context.startOffset
        val document = editor.document
        document.deleteString(startOffset, context.tailOffset)

        templateManager.startTemplate(editor, template, true, predefinedVarValues, null)
      } else {
        EditorModificationUtil.insertStringAtCaret(editor, " ")
      }
    }
  }
}
