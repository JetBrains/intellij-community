// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.highlighting

import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class MermaidPairMatcher : PairedBraceMatcherAdapter(MyPairedBraceMatcher(), MermaidLanguage) {
  companion object {
    private class MyPairedBraceMatcher : PairedBraceMatcher {
      private val pairs = arrayOf(
        BracePair(MermaidTokens.OPEN_CURLY, MermaidTokens.CLOSE_CURLY, false),
        BracePair(MermaidTokens.OPEN_ROUND, MermaidTokens.CLOSE_ROUND, false),
        BracePair(MermaidTokens.OPEN_SQUARE, MermaidTokens.CLOSE_SQUARE, false),

        BracePair(MermaidTokens.Flowchart.SUBGRAPH, MermaidTokens.END, true),
        BracePair(MermaidTokens.Sequence.LOOP, MermaidTokens.END, true),
        BracePair(MermaidTokens.Sequence.ALT, MermaidTokens.END, true),
        BracePair(MermaidTokens.Sequence.OPT, MermaidTokens.END, true),
        BracePair(MermaidTokens.Sequence.PAR, MermaidTokens.END, true),
        BracePair(MermaidTokens.Sequence.PAR_OVER, MermaidTokens.END, true),
        BracePair(MermaidTokens.Sequence.RECT, MermaidTokens.END, true),
        BracePair(MermaidTokens.Sequence.CRITICAL, MermaidTokens.END, true),
        BracePair(MermaidTokens.Sequence.BREAK, MermaidTokens.END, true),
        BracePair(MermaidTokens.Sequence.BOX, MermaidTokens.END, true),
        BracePair(MermaidTokens.NOTE, MermaidTokens.END, true),
        BracePair(MermaidTokens.Block.BLOCK, MermaidTokens.END, true),

        BracePair(MermaidTokens.Flowchart.STADIUM_START, MermaidTokens.Flowchart.STADIUM_END, false),
        BracePair(MermaidTokens.Flowchart.SUBROUTINE_START, MermaidTokens.Flowchart.SUBROUTINE_END, false),
        BracePair(MermaidTokens.Flowchart.CYLINDER_START, MermaidTokens.Flowchart.CYLINDER_END, false),
        BracePair(MermaidTokens.Flowchart.CIRCLE_START, MermaidTokens.Flowchart.CIRCLE_END, false),
        BracePair(MermaidTokens.Flowchart.ASYMMETRIC_START, MermaidTokens.CLOSE_SQUARE, false),
        BracePair(MermaidTokens.Flowchart.DIAMOND_START, MermaidTokens.Flowchart.DIAMOND_END, false),
        BracePair(MermaidTokens.Flowchart.HEXAGON_START, MermaidTokens.Flowchart.HEXAGON_END, false),
        BracePair(MermaidTokens.Flowchart.TRAP_START, MermaidTokens.Flowchart.INV_TRAP_END, false),
        BracePair(MermaidTokens.Flowchart.INV_TRAP_START, MermaidTokens.Flowchart.TRAP_END, false),
        BracePair(MermaidTokens.Flowchart.TRAP_START, MermaidTokens.Flowchart.TRAP_END, false),
        BracePair(MermaidTokens.Flowchart.INV_TRAP_START, MermaidTokens.Flowchart.INV_TRAP_END, false),
        BracePair(MermaidTokens.Flowchart.DOUBLE_CIRCLE_START, MermaidTokens.Flowchart.DOUBLE_CIRCLE_END, false),
        BracePair(MermaidTokens.Flowchart.SEP, MermaidTokens.Flowchart.SEP, false),

        BracePair(MermaidTokens.Mindmap.OPEN_ICON, MermaidTokens.Mindmap.CLOSE_ICON, false),
        BracePair(MermaidTokens.NODE_DESCR_START, MermaidTokens.NODE_DESCR_END, false),
        BracePair(MermaidTokens.Block.ARROW_DESCR_START, MermaidTokens.Block.ARROW_DESCR_END, false),

        BracePair(MermaidTokens.ANNOTATION_START, MermaidTokens.ANNOTATION_END, false),
        BracePair(MermaidTokens.Directives.OPEN_DIRECTIVE, MermaidTokens.Directives.CLOSE_DIRECTIVE, false)
      )

      override fun getPairs(): Array<BracePair> = pairs

      override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean {
        return true
      }

      override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int {
        return openingBraceOffset
      }
    }
  }
}
