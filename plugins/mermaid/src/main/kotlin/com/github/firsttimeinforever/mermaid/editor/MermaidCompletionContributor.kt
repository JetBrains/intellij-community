package com.github.firsttimeinforever.mermaid.editor

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens
import com.github.firsttimeinforever.mermaid.lang.psi.impl.MermaidFlowchartDocumentImpl
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.PlatformPatterns.*
import com.intellij.util.ProcessingContext

class MermaidCompletionContributor : CompletionContributor() {

  init {
    extend(
      CompletionType.BASIC,
      psiElement().inside(
        true, psiFile(), or(
          psiElement(MermaidTokens.Flowchart.FLOWCHART),
          psiElement(MermaidTokens.Pie.PIE),
          psiElement(MermaidTokens.Journey.JOURNEY)
        )
      ),
      MermaidDiagramCompletionProvider()
    )

    extend(
      CompletionType.BASIC,
      psiElement().afterLeaf(
        or(
          psiElement(MermaidTokens.Flowchart.FLOWCHART),
          psiElement(MermaidTokens.Flowchart.DIRECTION)
        )
      ),
      MermaidFlowchartDirectionCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      or(
        psiElement().inside(MermaidFlowchartDocumentImpl::class.java) //!!!
      ),
      MermaidFlowchartCompletionProvider()
    )

    extend(
      CompletionType.BASIC,
      psiElement().afterLeaf(
        or(
          psiElement(MermaidTokens.Pie.PIE),
          psiElement(MermaidTokens.EOL).afterLeaf(psiElement(MermaidTokens.Pie.PIE))
        )
      ),
      MermaidPieShowDataCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterLeaf(
        or(
          psiElement(MermaidTokens.Pie.PIE),
          psiElement(MermaidTokens.EOL)
        )
      ),
      MermaidPieTitleCompletionProvider()
    )
  }
}

class MermaidDiagramCompletionProvider : CompletionProvider<CompletionParameters>() {
  private val diagrams = listOf("pie", "journey", "flowchart", "sequenceDiagram")
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    result.addAllElements(diagrams.map { createKeywordLookupElement(it) })
  }

  private fun createKeywordLookupElement(keyword: String): LookupElement {
    val insertHandler = InsertHandler { ctx: InsertionContext, _: LookupElement? ->
      val editor = ctx.editor
      val document = editor.document
      // attempt to fix bug with completion of first element in document
      val lineStartOffset = document.getLineStartOffset(document.getLineNumber(ctx.startOffset))
      document.deleteString(lineStartOffset, ctx.tailOffset)
      EditorModificationUtil.insertStringAtCaret(editor, keyword)
    }
    return LookupElementBuilder
      .create(keyword)
      .withInsertHandler(insertHandler)
  }
}
