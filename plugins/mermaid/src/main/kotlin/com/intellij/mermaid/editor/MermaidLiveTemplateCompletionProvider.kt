package com.intellij.mermaid.editor

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager

abstract class MermaidLiveTemplateCompletionProvider :
  CompletionProvider<CompletionParameters>() {
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
        val startOffset = context.startOffset
        val document = editor.document
        document.deleteString(startOffset, context.tailOffset)

        templateManager.startTemplate(editor, template, true, null, object : TemplateEditingAdapter() {
          override fun templateFinished(template: Template, brokenOff: Boolean) {
            super.templateFinished(template, brokenOff)

            val project = editor.project
            if (project != null) {
              val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
              if (file != null) {

                val line = document.getLineNumber(startOffset)
                val startOffset = document.getLineStartOffset(line)
                val endOffset = document.getLineEndOffset(line + 1)

                PsiDocumentManager.getInstance(project).commitDocument(document)
                WriteCommandAction.runWriteCommandAction(project) {
                  CodeStyleManager.getInstance(project).adjustLineIndent(file, TextRange(startOffset, endOffset))
                }
              }
            }
          }
        })
      } else {
        EditorModificationUtil.insertStringAtCaret(editor, " ")
      }
    }
  }
}
