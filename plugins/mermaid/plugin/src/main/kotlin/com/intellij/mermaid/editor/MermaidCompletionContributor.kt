package com.intellij.mermaid.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.mermaid.lang.lexer.MermaidToken
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.WHITE_SPACES_WITHOUT_EOL
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.mermaid.lang.psi.MermaidDirective
import com.intellij.mermaid.lang.psi.children
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.*
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.prevLeafs
import com.intellij.psi.util.siblings
import com.intellij.util.ProcessingContext

class MermaidCompletionContributor : CompletionContributor() {

  init {
    extend(
      CompletionType.BASIC,
      psiElement().notInsideDiagram(),
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

    //region Flowchart
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.FLOWCHART_HEADER)),
      FlowchartCompletionProvider()
    )
    //endregion

    //region Pie
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
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.PIE_HEADER)),
      TitleCompletionProvider()
    )
    //endregion

    //region Sequence
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidTokens.Sequence.SEQUENCE)),
      SequenceCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidTokens.Sequence.SEQUENCE)),
      SequenceSimpleCompletionProvider("autonumber")
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideBlock(psiElement(MermaidTokens.Sequence.ALT)),
      SequenceSimpleCompletionProvider("else")
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideBlock(psiElement(MermaidTokens.Sequence.PAR)),
      SequenceSimpleCompletionProvider("and")
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideBlock(psiElement(MermaidTokens.Sequence.CRITICAL)),
      SequenceSimpleCompletionProvider("option")
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideBlock(
        or(
          psiElement(MermaidTokens.Sequence.LOOP),
          psiElement(MermaidTokens.Sequence.ALT),
          psiElement(MermaidTokens.Sequence.OPT),
          psiElement(MermaidTokens.Sequence.PAR),
          psiElement(MermaidTokens.Sequence.CRITICAL),
          psiElement(MermaidTokens.Sequence.BREAK),
          psiElement(MermaidTokens.Sequence.RECT)
        )
      ),
      SequenceSimpleCompletionProvider("end")
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterLeaf(psiElement(MermaidTokens.Sequence.AUTONUMBER)),
      SequenceSimpleCompletionProvider("off")
    )
    //endregion

    //region Journey
    extend(
      CompletionType.BASIC,
      or(
        psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidTokens.Journey.JOURNEY)),
        psiElement().afterLeaf(psiElement(MermaidTokens.Journey.JOURNEY))
      ),
      TitleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidTokens.Journey.JOURNEY)),
      BranchCompletionProvider("section")
    )
    //endregion

    //region Class Diagram
    extend(
      CompletionType.BASIC,
      psiElement().atTopLevelOfDiagram(psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM)),
      ClassDiagramSimpleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      and(
        psiElement().afterLeaf(psiElement(MermaidTokens.ANNOTATION_START)),
        psiElement().insideDiagram(psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM))
      ),
      ClassDiagramAnnotationCompletionProvider()
    )
    //endregion

    //region State Diagram
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM)),
      MermaidSimpleCompletionProvider(listOf("state", "direction"))
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM)),
      StateDiagramLiveTemplateCompletionProvider("note")
    )
    extend(
      CompletionType.BASIC,
      and(
        psiElement().insideBlock(psiElement(MermaidTokens.NOTE)),
        psiElement().insideDiagram(psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM))
      ),
      SequenceSimpleCompletionProvider("end note")
    )
    extend(
      CompletionType.BASIC,
      and(
        psiElement().afterLeaf(psiElement(MermaidTokens.NOTE)),
        psiElement().insideDiagram(psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM))
      ),
      MermaidSimpleCompletionProvider(listOf("right of", "left of"))
    )
    extend(
      CompletionType.BASIC,
      and(
        psiElement().insideDiagram(psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM)),
        psiElement().afterLeaf(psiElement(MermaidTokens.DOUBLE_QUOTE)),
      ),
      MermaidSimpleCompletionProvider(listOf("as"))
    )
    extend(
      CompletionType.BASIC,
      and(
        psiElement().afterLeaf(psiElement(MermaidTokens.ANNOTATION_START)),
        psiElement().insideDiagram(psiElement(MermaidTokens.StateDiagram.STATE_DIAGRAM)),
      ),
      StateDiagramAnnotationCompletionProvider()
    )
    //endregion

    //region Gantt
    extend(
      CompletionType.BASIC,
      or(
        psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidTokens.Gantt.GANTT)),
        psiElement().afterLeaf(psiElement(MermaidTokens.Gantt.GANTT))
      ),
      TitleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagram(psiElement(MermaidTokens.Gantt.GANTT)),
      GanttSimpleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidTokens.Gantt.GANTT)),
      GanttTopLevelCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidTokens.Gantt.GANTT)),
      BranchCompletionProvider("section")
    )
    //endregion

    //region Requirement
    extend(
      CompletionType.BASIC,
      psiElement().atTopLevelOfDiagram(psiElement(MermaidTokens.Requirement.REQUIREMENT_DIAGRAM)),
      RequirementCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterLeafSkipping(
        or(
          psiElement(MermaidTokens.COLON),
          psiElement().whitespace()
        ),
        psiElement(MermaidTokens.Requirement.RISK)
      ),
      RequirementRiskCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterLeafSkipping(
        or(
          psiElement(MermaidTokens.COLON),
          psiElement().whitespace()
        ),
        psiElement(MermaidTokens.Requirement.VERIFY_METHOD)
      ),
      RequirementVerifyMethodCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterLeafSkipping(
        psiElement().whitespace(),
        psiElement(MermaidTokens.Requirement.REQ_LINE)
      ),
      RequirementRelationshipCompletionProvider()
    )
    //endregion

    //region Git Graph
    extend(
      CompletionType.BASIC,
      psiElement().atTopLevelOfDiagram(psiElement(MermaidTokens.GitGraph.GIT_GRAPH)),
      GitGraphSimpleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideGitGraphStatement(psiElement(MermaidElements.COMMIT_STATEMENT)),
      GitGraphCommitCompletionProvider(listOf("id", "tag", "type", "msg"))
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideGitGraphStatement(psiElement(MermaidElements.MERGE_STATEMENT)),
      GitGraphCommitCompletionProvider(listOf("id", "tag", "type"))
    )
    extend(
      CompletionType.BASIC,
      or(
        psiElement().afterSiblingSkippingElementsAndWhitespaces(MermaidTokens.GitGraph.CHERRY_PICK),
        psiElement().insideGitGraphStatement(psiElement(MermaidElements.CHERRY_PICK_STATEMENT))
      ),
      GitGraphCommitCompletionProvider(listOf("id", "tag"))
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterSiblingSkippingElementsAndWhitespaces(
        MermaidTokens.TYPE,
        psiElement(MermaidTokens.COLON)
      ),
      GitGraphCommitTypeCompletionProvider()
    )
    //endregion

    //region C4
    extend(
      CompletionType.BASIC,
      psiElement().atTopLevelOfDiagram(psiElement(MermaidElements.C_4_HEADER)),
      TitleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.C_4_HEADER)),
      C4CompletionProvider()
    )
    //endregion
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
        val siblings = psiElement.siblings(forward = false, withSelf = false)
        val firstNonSpaceSibling = siblings.filter { it.elementType !in WHITE_SPACES_WITHOUT_EOL }
          .firstOrNull()
        val rightAfterDiagramHeader = pattern.accepts(firstNonSpaceSibling)

        val file = psiElement.containingFile
        for (child in file.children) {
          if (whitespaceCommentEmptyErrorEolOrDirective().accepts(child, context)) {
            continue
          }
          return pattern.accepts(child, context) && !rightAfterDiagramHeader
        }
        return false
      }
    })
  }

  private fun PsiElementPattern.Capture<PsiElement>.notInsideAnyStatement(): PsiElementPattern.Capture<PsiElement> {
    return with(object : PatternCondition<PsiElement>("insideStatement") {
      override fun accepts(psiElement: PsiElement, context: ProcessingContext): Boolean {
        val prevLeafs = psiElement.prevLeafs
        for (leaf in prevLeafs) {
          if (whitespaceCommentEmptyOrError().accepts(leaf, context)) {
            continue
          }
          return leaf.elementType == MermaidTokens.EOL
        }
        return true
      }
    })
  }

  private fun PsiElementPattern.Capture<PsiElement>.insideDiagramAndNotAtStatement(pattern: ElementPattern<in PsiElement>): PsiElementPattern.Capture<PsiElement> {
    return insideDiagram(pattern).and(notInsideAnyStatement())
  }

  private fun PsiElementPattern.Capture<PsiElement>.notInsideDiagram(): PsiElementPattern.Capture<PsiElement> {
    return with(object : PatternCondition<PsiElement>("notInsideDiagram") {
      override fun accepts(psiElement: PsiElement, context: ProcessingContext): Boolean {
        return psiElement.containingFile.children().map { whitespaceCommentEmptyErrorEolOrDirective().accepts(it) }.all { it }
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

  private fun PsiElementPattern.Capture<PsiElement>.afterSiblingSkippingElementsAndWhitespaces(
    attribute: MermaidToken,
    vararg skip: ElementPattern<in PsiElement>
  ): PsiElementPattern.Capture<PsiElement> {
    return withParent(
      psiElement().afterSiblingSkipping(
        or(
          psiElement().whitespace(),
          *skip
        ),
        psiElement(attribute)
      )
    )
  }

  private fun PsiElementPattern.Capture<PsiElement>.atTopLevelOfDiagram(
    diagramPattern: ElementPattern<in PsiElement>
  ): PsiElementPattern.Capture<PsiElement> {
    return insideDiagramAndNotAtStatement(diagramPattern).with(object : PatternCondition<PsiElement>("inTopLevelOfDiagram") {
      override fun accepts(psiElement: PsiElement, context: ProcessingContext): Boolean {
        var element = psiElement
        if (element !is PsiErrorElement && element.parent is PsiErrorElement) {
          element = element.parent
        }
        val sibling = element.siblings(forward = false, withSelf = false)
          .filter { it !is PsiErrorElement && it.elementType !in MermaidTokenTypeSets.WHITE_SPACES }
          .firstOrNull()

        return sibling?.elementType in MermaidTokenTypeSets.DIAGRAM_DOCUMENTS
      }
    })
  }

  private fun PsiElementPattern.Capture<PsiElement>.withLastDocumentLine(
    documentLinePattern: ElementPattern<in PsiElement>
  ): PsiElementPattern.Capture<PsiElement> {
    return with(object : PatternCondition<PsiElement>("withLastDocumentLine") {
      override fun accepts(psiElement: PsiElement, context: ProcessingContext): Boolean {
        for (child in psiElement.containingFile.children) {
          if (child.elementType in MermaidTokenTypeSets.DIAGRAM_DOCUMENTS) {
            val lastLine = child.lastChild
            val lastElement = lastLine.lastChild
            return documentLinePattern.accepts(lastElement)
          }
        }
        return false
      }
    })
  }

  private fun PsiElementPattern.Capture<PsiElement>.insideGitGraphStatement(
    statementPattern: ElementPattern<in PsiElement>
  ): PsiElementPattern.Capture<PsiElement> {
    return withLastDocumentLine(statementPattern).and(not(psiElement().afterLeaf(psiElement(MermaidTokens.COLON))))
  }
}
