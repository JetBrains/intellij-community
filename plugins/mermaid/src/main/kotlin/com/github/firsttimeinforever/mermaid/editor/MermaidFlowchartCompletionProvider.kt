package com.github.firsttimeinforever.mermaid.editor

import com.github.firsttimeinforever.mermaid.lang.psi.impl.MermaidSubgraphStatementImpl
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project
import com.intellij.util.ProcessingContext

class MermaidFlowchartCompletionProvider : CompletionProvider<CompletionParameters>() {
  private val PRIORITY = 10.0

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addElement(createKeywordLookupElement(project, "subgraph"))

    var psiElement = parameters.position
    while (psiElement.parent != null) {
      psiElement = psiElement.parent
      if (psiElement is MermaidSubgraphStatementImpl) {
        result.addElement(LookupElementBuilder.create("direction"))
        break
      }
    }
  }

  private fun createKeywordLookupElement(project: Project, keyword: String): LookupElement {
    val templateManager = TemplateManager.getInstance(project) as TemplateManagerImpl
    val template = TemplateSettings.getInstance().getTemplateById("mermaid_$keyword")
    val insertHandler = createTemplateBasedInsertHandler(templateManager, template)
    return PrioritizedLookupElement.withPriority(
      LookupElementBuilder
        .create(keyword)
        .withBoldness(true)
        .withInsertHandler(insertHandler), PRIORITY
    )
  }

  private fun createTemplateBasedInsertHandler(
    templateManager: TemplateManagerImpl,
    template: Template?
  ): InsertHandler<LookupElement> {
    return InsertHandler { context: InsertionContext, _: LookupElement? ->
      val editor = context.editor
      if (template != null) {
        editor.document.deleteString(context.startOffset, context.tailOffset)
        templateManager.startTemplate(editor, template)
      } else {
        EditorModificationUtil.insertStringAtCaret(editor, " ")
      }
    }
  }
}

class MermaidFlowchartDirectionCompletionProvider : CompletionProvider<CompletionParameters>() {
  private val directions = listOf("LR", "RL", "TB", "BT", "TD", "BR", "<", ">", "^", "v")

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    result.addAllElements(directions.map { LookupElementBuilder.create(it) })
  }
}
