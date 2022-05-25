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

abstract class MermaidLiveTemplateCompletionProvider(protected var deleteIndent: Boolean = false) : CompletionProvider<CompletionParameters>() {
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

        val startOffset = context.startOffset
        val document = editor.document
        document.deleteString(startOffset, context.tailOffset)

        if (deleteIndent) {
          val lineStartOffset = document.getLineStartOffset(document.getLineNumber(startOffset))
          val currIndent = startOffset - lineStartOffset
          if (currIndent > indentOptions.INDENT_SIZE ) {
            document.deleteString(startOffset - indentOptions.INDENT_SIZE, startOffset)
          }
        }

        templateManager.startTemplate(editor, template)
      } else {
        EditorModificationUtil.insertStringAtCaret(editor, " ")
      }
    }
  }
}
