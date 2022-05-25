package com.github.firsttimeinforever.mermaid.editor

import com.github.firsttimeinforever.mermaid.lang.MermaidFileType
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project

abstract class MermaidLiveTemplateCompletionProvider(private val deleteIndent: Boolean = false) : CompletionProvider<CompletionParameters>() {
  private val priority = 10.0
  
  protected fun createKeywordLookupElement(project: Project, keyword: String): LookupElement {
    val templateManager = TemplateManager.getInstance(project) as TemplateManagerImpl
    val template = TemplateSettings.getInstance().getTemplateById("mermaid_$keyword")
    val insertHandler = createTemplateBasedInsertHandler(templateManager, template)
    return PrioritizedLookupElement.withPriority(
      LookupElementBuilder
        .create(keyword)
        .withBoldness(true)
        .withInsertHandler(insertHandler), priority
    )
  }

  private fun createTemplateBasedInsertHandler(
    templateManager: TemplateManagerImpl,
    template: Template?
  ): InsertHandler<LookupElement> {
    return InsertHandler { context: InsertionContext, _: LookupElement? ->
      val editor = context.editor
      if (template != null) {
        val settings = CodeStyle.getSettings(editor)
        val indentOptions = settings.getIndentOptions(MermaidFileType)

        val startOffset = if (deleteIndent) {
          context.startOffset - indentOptions.INDENT_SIZE
        } else {
          context.startOffset
        }
        editor.document.deleteString(startOffset, context.tailOffset)
        templateManager.startTemplate(editor, template)
      } else {
        EditorModificationUtil.insertStringAtCaret(editor, " ")
      }
    }
  }
}
