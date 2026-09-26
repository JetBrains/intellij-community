// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.mermaid.lang.lexer.MermaidToken
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.WHITE_SPACES_WITHOUT_EOL
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.mermaid.lang.psi.MermaidDirective
import com.intellij.mermaid.lang.psi.MermaidFrontmatter
import com.intellij.mermaid.lang.psi.children
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.and
import com.intellij.patterns.PlatformPatterns.not
import com.intellij.patterns.PlatformPatterns.or
import com.intellij.patterns.PlatformPatterns.psiElement
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
      psiElement().afterLeaf(psiElement(MermaidTokens.DIRECTION)),
      DirectionCompletionProvider()
    )

    //region Flowchart
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.FLOWCHART_HEADER)),
      FlowchartCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterLeaf(psiElement(MermaidTokens.Flowchart.FLOWCHART)),
      ExtendedDirectionCompletionProvider()
    )
    //endregion

    //region Pie
    extend(
      CompletionType.BASIC,
      psiElement().afterPieToken(),
      PieShowDataCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      or(
        psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.PIE_HEADER)),
        psiElement().afterLeaf(psiElement(MermaidTokens.Pie.PIE))
      ),
      TitleCompletionProvider()
    )
    //endregion

    //region Sequence
    extend(
      CompletionType.BASIC,
      or(
        psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.SEQUENCE_HEADER))
          .andNot(psiElement().insideBlock(psiElement(MermaidTokens.Sequence.BOX))),
        psiElement().afterLeaf(psiElement(MermaidTokens.Sequence.SEQUENCE))
      ),
      TitleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement()
        .insideDiagramAndNotAtStatement(psiElement(MermaidElements.SEQUENCE_HEADER))
        .andNot(psiElement().insideBlock(psiElement(MermaidTokens.Sequence.BOX))),
      SequenceBlockCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.SEQUENCE_HEADER)),
      SequenceActorParticipantCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.SEQUENCE_HEADER))
        .andNot(psiElement().insideBlock(psiElement(MermaidTokens.Sequence.BOX))),
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
      psiElement().insideBlock(psiElement(MermaidTokens.Sequence.PAR_OVER)),
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
          psiElement(MermaidTokens.Sequence.PAR_OVER),
          psiElement(MermaidTokens.Sequence.CRITICAL),
          psiElement(MermaidTokens.Sequence.BREAK),
          psiElement(MermaidTokens.Sequence.RECT),
          psiElement(MermaidTokens.Sequence.BOX),
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
        psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.JOURNEY_HEADER)),
        psiElement().afterLeaf(psiElement(MermaidTokens.Journey.JOURNEY))
      ),
      TitleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.JOURNEY_HEADER)),
      BranchCompletionProvider("section")
    )
    //endregion

    //region Class Diagram
    extend(
      CompletionType.BASIC,
      psiElement().atTopLevelOfDiagram(psiElement().isClassDiagramHeader()),
      ClassDiagramSimpleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      and(
        psiElement().afterLeaf(psiElement(MermaidTokens.ANNOTATION_START)),
        psiElement().insideDiagram(psiElement().isClassDiagramHeader())
      ),
      ClassDiagramAnnotationCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().atTopLevelOfDiagram(psiElement().isClassDiagramHeader()),
      ClassDiagramLiveTemplateCompletionProvider("namespace")
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideBlock(psiElement(MermaidTokens.ClassDiagram.NAMESPACE)),
      MermaidSimpleCompletionProvider(listOf("class"))
    )
    //endregion

    //region State Diagram
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.STATE_HEADER)),
      MermaidSimpleCompletionProvider(listOf("state", "direction"))
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.STATE_HEADER)),
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
        psiElement().insideDiagram(psiElement(MermaidElements.STATE_HEADER))
      ),
      MermaidSimpleCompletionProvider(listOf("right of", "left of"))
    )
    extend(
      CompletionType.BASIC,
      and(
        psiElement().insideDiagram(psiElement(MermaidElements.STATE_HEADER)),
        psiElement().afterLeaf(psiElement(MermaidTokens.DOUBLE_QUOTE)),
      ),
      MermaidSimpleCompletionProvider(listOf("as"))
    )
    extend(
      CompletionType.BASIC,
      and(
        psiElement().afterLeaf(psiElement(MermaidTokens.ANNOTATION_START)),
        psiElement().insideDiagram(psiElement(MermaidElements.STATE_HEADER)),
      ),
      StateDiagramAnnotationCompletionProvider()
    )
    //endregion

    //region Gantt
    extend(
      CompletionType.BASIC,
      or(
        psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.GANTT_HEADER)),
        psiElement().afterLeaf(psiElement(MermaidTokens.Gantt.GANTT))
      ),
      TitleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagram(psiElement(MermaidElements.GANTT_HEADER)),
      GanttSimpleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.GANTT_HEADER)),
      GanttTopLevelCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.GANTT_HEADER)),
      BranchCompletionProvider("section")
    )
    //endregion

    //region Requirement
    extend(
      CompletionType.BASIC,
      psiElement().atTopLevelOfDiagram(psiElement(MermaidElements.REQUIREMENT_DIAGRAM_HEADER)),
      RequirementCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterLeafSkipping(
        or(
          psiElement(MermaidTokens.COLON),
          psiElement().whitespaceCommentEmptyOrError()
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
          psiElement().whitespaceCommentEmptyOrError()
        ),
        psiElement(MermaidTokens.Requirement.VERIFY_METHOD)
      ),
      RequirementVerifyMethodCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterLeafSkipping(
        psiElement().whitespaceCommentEmptyOrError(),
        psiElement(MermaidTokens.Requirement.REQ_LINE)
      ),
      RequirementRelationshipCompletionProvider()
    )
    //endregion

    //region Git Graph
    extend(
      CompletionType.BASIC,
      psiElement().atTopLevelOfDiagram(psiElement(MermaidElements.GIT_GRAPH_HEADER)),
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
      GitGraphCommitCompletionProvider(listOf("id", "tag", "parent"))
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterSiblingSkippingElementsAndWhitespaces(
        MermaidTokens.TYPE,
        psiElement(MermaidTokens.COLON)
      ),
      GitGraphCommitTypeCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterLeaf(psiElement(MermaidTokens.GitGraph.GIT_GRAPH)),
      GitGraphDirectionCompletionProvider()
    )
    //endregion

    //region C4
    extend(
      CompletionType.BASIC,
      psiElement().atTopLevelOfDiagram(psiElement().isC4Header()),
      TitleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement().isC4Header()),
      C4CompletionProvider()
    )
    //endregion

    //region Timeline
    extend(
      CompletionType.BASIC,
      or(
        psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.TIMELINE_HEADER)),
        psiElement().afterLeaf(psiElement(MermaidTokens.Timeline.TIMELINE))
      ),
      TitleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.TIMELINE_HEADER)),
      BranchCompletionProvider("section")
    )
    //endregion

    //region Quadrant
    extend(
      CompletionType.BASIC,
      or(
        psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.QUADRANT_HEADER)),
        psiElement().afterLeaf(psiElement(MermaidTokens.Quadrant.QUADRANT_CHART))
      ),
      TitleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().atTopLevelOfDiagram(psiElement(MermaidElements.QUADRANT_HEADER)),
      QuadrantCompletionProvider()
    )
    //endregion

    //region XYChart
    extend(
      CompletionType.BASIC,
      or(
        psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.XY_CHART_HEADER)),
        psiElement().afterLeaf(psiElement(MermaidTokens.XYChart.XY_CHART))
      ),
      TitleCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterLeaf(psiElement(MermaidTokens.XYChart.XY_CHART)),
      XYChartOrientationCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().atTopLevelOfDiagram(psiElement(MermaidElements.XY_CHART_HEADER)),
      XYChartCompletionProvider()
    )
    //endregion

    //region Block
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.BLOCK_DIAGRAM_HEADER)),
      BlockCompletionProvider()
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagramAndNotAtStatement(psiElement(MermaidElements.BLOCK_DIAGRAM_HEADER)),
      MermaidSimpleCompletionProvider(listOf("columns"))
    )
    extend(
      CompletionType.BASIC,
      psiElement().afterSiblingSkippingElementsAndWhitespaces(MermaidTokens.Block.COLUMNS),
      MermaidSimpleCompletionProvider(listOf("auto"))
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideDiagram(psiElement(MermaidElements.BLOCK_DIAGRAM_HEADER)),
      MermaidSimpleCompletionProvider(listOf("space"))
    )
    extend(
      CompletionType.BASIC,
      psiElement().insideBlock(psiElement(MermaidTokens.Block.BLOCK)),
      MermaidSimpleCompletionProvider(listOf("end"))
    )
    //endregion
  }

  private fun PsiElementPattern.Capture<PsiElement>.insideBlock(pattern: ElementPattern<in PsiElement>): PsiElementPattern.Capture<PsiElement> {
    return with(object : PatternCondition<PsiElement>("insideBlock") {
      override fun accepts(psiElement: PsiElement, context: ProcessingContext): Boolean {
        var element = psiElement
        if (element !is PsiErrorElement && element.parent is PsiErrorElement) {
          element = element.parent
        }
        for (sibling in element.siblings(forward = false, withSelf = false)) {
          if (psiElement(MermaidTokens.END).accepts(sibling, context)) {
            return false
          }
          if (pattern.accepts(sibling, context)) {
            return true
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
          if (whitespaceCommentEmptyErrorEolDirectiveOrFrontmatter().accepts(child, context)) {
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
        return psiElement.containingFile.children()
          .map { whitespaceCommentEmptyErrorEolDirectiveOrFrontmatter().accepts(it) }.all { it }
      }
    })
  }


  private fun PsiElementPattern.Capture<PsiElement>.whitespaceCommentEmptyErrorEolDirectiveOrFrontmatter(): PsiElementPattern.Capture<PsiElement> {
    return andOr(
      psiElement().whitespaceCommentEmptyOrError(),
      psiElement(MermaidTokens.EOL),
      psiElement(MermaidDirective::class.java),
      psiElement(MermaidTokens.Frontmatter.FRONTMATTER_START),
      psiElement(MermaidTokens.Frontmatter.FRONTMATTER_VALUE),
      psiElement(MermaidTokens.Frontmatter.FRONTMATTER_END),
      psiElement(MermaidFrontmatter::class.java),
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
    return insideDiagramAndNotAtStatement(diagramPattern).with(object :
      PatternCondition<PsiElement>("inTopLevelOfDiagram") {
      override fun accepts(psiElement: PsiElement, context: ProcessingContext): Boolean {
        var element = psiElement
        if (element !is PsiErrorElement && element.parent is PsiErrorElement) {
          element = element.parent
        }
        val sibling = element.siblings(forward = false, withSelf = false)
          .filter { it !is PsiErrorElement && it.elementType !in MermaidTokenTypeSets.WHITE_SPACES }
          .firstOrNull()

        return sibling?.elementType in MermaidTokenTypeSets.DIAGRAM_BODIES_AND_BLOCKS || diagramPattern.accepts(sibling)
      }
    })
  }

  private fun PsiElementPattern.Capture<PsiElement>.withLastDocumentLine(
    documentLinePattern: ElementPattern<in PsiElement>
  ): PsiElementPattern.Capture<PsiElement> {
    return with(object : PatternCondition<PsiElement>("withLastDocumentLine") {
      override fun accepts(psiElement: PsiElement, context: ProcessingContext): Boolean {
        return psiElement.containingFile.children()
          .find { it.elementType in MermaidTokenTypeSets.DIAGRAM_BODIES_AND_BLOCKS }
          .let {
            documentLinePattern.accepts(it?.lastChild)
          }
      }
    })
  }

  private fun PsiElementPattern.Capture<PsiElement>.insideGitGraphStatement(
    statementPattern: ElementPattern<in PsiElement>
  ): PsiElementPattern.Capture<PsiElement> {
    return withLastDocumentLine(statementPattern).and(not(psiElement().afterLeaf(psiElement(MermaidTokens.COLON))))
  }

  private fun PsiElementPattern.Capture<PsiElement>.isC4Header(): PsiElementPattern.Capture<PsiElement> {
    return andOr(
      psiElement(MermaidElements.C_4_HEADER),
      psiElement(MermaidTokens.C4.C4_CONTEXT),
      psiElement(MermaidTokens.C4.C4_CONTAINER),
      psiElement(MermaidTokens.C4.C4_COMPONENT),
      psiElement(MermaidTokens.C4.C4_DYNAMIC),
      psiElement(MermaidTokens.C4.C4_DEPLOYMENT),
    )
  }

  private fun PsiElementPattern.Capture<PsiElement>.isClassDiagramHeader(): PsiElementPattern.Capture<PsiElement> {
    return andOr(
      psiElement(MermaidElements.CLASS_DIAGRAM_HEADER),
      psiElement(MermaidTokens.ClassDiagram.CLASS_DIAGRAM),
    )
  }

  private fun PsiElementPattern.Capture<PsiElement>.afterPieToken(): PsiElementPattern.Capture<PsiElement> {
    return with(object : PatternCondition<PsiElement>("afterPieToken") {
      override fun accepts(psiElement: PsiElement, context: ProcessingContext): Boolean {
        var element = psiElement
        if (element !is PsiErrorElement && element.parent is PsiErrorElement) {
          element = element.parent
        }
        return psiElement().afterLeafSkipping(
          or(
            psiElement().whitespaceCommentEmptyOrError(),
            psiElement(MermaidTokens.EOL)
          ),
          psiElement(MermaidTokens.Pie.PIE)
        ).accepts(element)
      }
    })
  }
}
