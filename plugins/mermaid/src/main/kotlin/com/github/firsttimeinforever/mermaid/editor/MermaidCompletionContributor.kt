package com.github.firsttimeinforever.mermaid.editor

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens
import com.github.firsttimeinforever.mermaid.lang.psi.impl.MermaidFlowchartDocumentImpl
import com.github.firsttimeinforever.mermaid.lang.psi.impl.MermaidPieDocumentImpl
import com.github.firsttimeinforever.mermaid.lang.psi.impl.MermaidSequenceDocumentImpl
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.*
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext

class MermaidCompletionContributor : CompletionContributor() {

  init {
    extend(
      CompletionType.BASIC,
      psiElement().inside(
        true, psiFile(), or(
          psiElement(MermaidTokens.Flowchart.FLOWCHART),
          psiElement(MermaidTokens.Pie.PIE),
          psiElement(MermaidTokens.Journey.JOURNEY),
          psiElement(MermaidTokens.Sequence.SEQUENCE)
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
      psiElement().inside(MermaidFlowchartDocumentImpl::class.java),
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
      psiElement().withParent(
        psiElement().afterSiblingSkipping(
          not(psiElement(MermaidTokens.Pie.PIE)).andOr(not(psiElement(MermaidPieDocumentImpl::class.java))),
          or(psiElement(MermaidTokens.Pie.PIE), psiElement(MermaidPieDocumentImpl::class.java))
        )
      ),
      MermaidPieTitleCompletionProvider()
    )

    extend(
      CompletionType.BASIC,
      psiElement().afterSiblingSkipping(
        not(psiElement(MermaidSequenceDocumentImpl::class.java)),
        psiElement(MermaidSequenceDocumentImpl::class.java)
      ),
      MermaidSequenceCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideBlock(psiElement(MermaidTokens.Sequence.ALT)),
      MermaidSequenceBranchCompletionProvider("else")
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideBlock(psiElement(MermaidTokens.Sequence.PAR)),
      MermaidSequenceBranchCompletionProvider("and")
    )
  }

  private fun PsiElementPattern.Capture<PsiElement>.insideBlock(pattern: ElementPattern<in PsiElement>): PsiElementPattern.Capture<PsiElement> {
    return with(object : PatternCondition<PsiElement>("insideBlock") {
      override fun accepts(psiElement: PsiElement, context: ProcessingContext): Boolean {
        val parent = psiElement.parent
        val children = parent.children
        var i = listOf(*children).indexOf(psiElement)
        while (--i >= 0) {
          if (psiElement(MermaidTokens.END).accepts(children[i], context)) {
            return false
          }
          if (pattern.accepts(children[i], context)) {
            return true
          }
        }
        return false
      }
    })
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
