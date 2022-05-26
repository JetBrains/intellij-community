package com.github.firsttimeinforever.mermaid.editor

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens
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
      psiElement(),
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
      MermaidDirectionCompletionProvider()
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
      psiElement(),
      MermaidTitleCompletionProvider(MermaidTokens.Pie.PIE, MermaidPieDocumentImpl::class.java)
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
      MermaidBranchCompletionProvider("else")
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideBlock(psiElement(MermaidTokens.Sequence.PAR)),
      MermaidBranchCompletionProvider("and")
    )

    extend(
      CompletionType.BASIC,
      psiElement(),
      MermaidTitleCompletionProvider(MermaidTokens.Journey.JOURNEY, MermaidJourneyDocumentImpl::class.java)
    )
    extend(
      CompletionType.BASIC,
      psiElement().inside(psiElement().insideBlock(psiElement(MermaidTokens.Journey.JOURNEY))),
      MermaidBranchCompletionProvider("section")
    )

    extend(
      CompletionType.BASIC,
      psiElement().afterSiblingSkipping(
        not(psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM)),
        psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM)
      ),
      MermaidClassDiagramSimpleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      or(
        psiElement().insideBlock(psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM)),
        psiElement().inside(psiElement().insideBlock(psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM)))
      ),
      MermaidClassDiagramCompletionProvider()
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
      MermaidClassDiagramAnnotationCompletionProvider()
    )

    extend(
      CompletionType.BASIC,
      psiElement().afterLeafSkipping(
        not(psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM)),
        psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM)
      ),
      MermaidStateDiagramSimpleCompletionProvider()
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
      MermaidStateDiagramAnnotationCompletionProvider()
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
}
