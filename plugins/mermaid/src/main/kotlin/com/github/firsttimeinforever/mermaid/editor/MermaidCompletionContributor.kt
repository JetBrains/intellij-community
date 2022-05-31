package com.github.firsttimeinforever.mermaid.editor

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens
import com.github.firsttimeinforever.mermaid.lang.parser.MermaidElements
import com.github.firsttimeinforever.mermaid.lang.psi.MermaidDirective
import com.github.firsttimeinforever.mermaid.lang.psi.impl.MermaidFlowchartDocumentImpl
import com.github.firsttimeinforever.mermaid.lang.psi.impl.MermaidJourneyDocumentImpl
import com.github.firsttimeinforever.mermaid.lang.psi.impl.MermaidPieDocumentImpl
import com.github.firsttimeinforever.mermaid.lang.psi.impl.MermaidSequenceDocumentImpl
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
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
      not(psiElement().insideDiagram(psiElement().diagramHeader())),
      MermaidDiagramCompletionProvider()
    )

    extend(
      CompletionType.BASIC,
      psiElement().afterLeaf(
        or(
          psiElement(MermaidTokens.Flowchart.FLOWCHART),
          psiElement(MermaidTokens.DIRECTION)
        )
      ),
      DirectionCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().inside(MermaidFlowchartDocumentImpl::class.java),
      FlowchartCompletionProvider()
    )

    extend(
      CompletionType.BASIC,
      psiElement().afterLeaf(
        or(
          psiElement(MermaidTokens.Pie.PIE),
          psiElement(MermaidTokens.EOL).afterLeaf(psiElement(MermaidTokens.Pie.PIE))
        )
      ),
      PieShowDataCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement(),
      TitleCompletionProvider(MermaidTokens.Pie.PIE, MermaidPieDocumentImpl::class.java)
    )

    extend(
      CompletionType.BASIC,
      psiElement().afterSiblingSkipping(
        not(psiElement(MermaidSequenceDocumentImpl::class.java)),
        psiElement(MermaidSequenceDocumentImpl::class.java)
      ),
      SequenceCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideBlock(psiElement(MermaidTokens.Sequence.ALT)),
      BranchCompletionProvider("else")
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideBlock(psiElement(MermaidTokens.Sequence.PAR)),
      BranchCompletionProvider("and")
    )

    extend(
      CompletionType.BASIC,
      psiElement(),
      TitleCompletionProvider(MermaidTokens.Journey.JOURNEY, MermaidJourneyDocumentImpl::class.java)
    )
    extend(
      CompletionType.BASIC,
      psiElement().inside(psiElement().insideBlock(psiElement(MermaidTokens.Journey.JOURNEY))),
      BranchCompletionProvider("section")
    )

    extend(
      CompletionType.BASIC,
      psiElement().afterSiblingSkipping(
        not(psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM)),
        psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM)
      ),
      ClassDiagramSimpleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      or(
        psiElement().insideBlock(psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM)),
        psiElement().inside(psiElement().insideBlock(psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM)))
      ),
      ClassDiagramCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      and(
        psiElement().afterLeaf(psiElement(MermaidTokens.ANNOTATION_START)),
        or(
          psiElement().insideBlock(psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM)),
          psiElement().inside(psiElement().insideBlock(psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM)))
        )
      ),
      ClassDiagramAnnotationCompletionProvider()
    )

    extend(
      CompletionType.BASIC,
      psiElement().insideDiagram(psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM)),
      StateDiagramSimpleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      and(
        psiElement().afterLeaf(psiElement(MermaidTokens.ANNOTATION_START)),
        or(
          psiElement().insideBlock(psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM)),
          psiElement().inside(psiElement().insideBlock(psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM)))
        )
      ),
      StateDiagramAnnotationCompletionProvider()
    )
  }

  private fun PsiElementPattern.Capture<PsiElement>.insideBlock(pattern: ElementPattern<in PsiElement>): PsiElementPattern.Capture<PsiElement> {
    return with(object : PatternCondition<PsiElement>("insideBlock") {
      override fun accepts(psiElement: PsiElement, context: ProcessingContext): Boolean {
        val parent = psiElement.parent
        if (parent != null) {
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
        }
        return false
      }
    })
  }

  private fun PsiElementPattern.Capture<PsiElement>.insideDiagram(pattern: ElementPattern<in PsiElement>): PsiElementPattern.Capture<PsiElement> {
    return with(object : PatternCondition<PsiElement>("insideDiagram") {
      override fun accepts(psiElement: PsiElement, context: ProcessingContext): Boolean {
        val file = psiElement.containingFile
        for (child in file.children) {
          if (whitespaceCommentEmptyErrorEolOrDirective().accepts(child, context)) {
            continue
          }
          return pattern.accepts(child, context)
        }
        return false
      }
    })
  }

  private fun PsiElementPattern.Capture<PsiElement>.whitespaceCommentEmptyErrorEolOrDirective(): PsiElementPattern.Capture<PsiElement> {
    return andOr(
      psiElement().whitespaceCommentEmptyOrError(),
      psiElement(MermaidTokens.EOL),
      psiElement(MermaidDirective::class.java)
    )
  }

  private fun PsiElementPattern.Capture<PsiElement>.diagramHeader(): PsiElementPattern.Capture<PsiElement> {
    return andOr(
      psiElement(MermaidElements.PIE_HEADER),
      psiElement(MermaidElements.FLOWCHART_HEADER),
      psiElement(MermaidTokens.Sequence.SEQUENCE),
      psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM),
      psiElement(MermaidTokens.Journey.JOURNEY),
      psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM)
    )
  }
}
