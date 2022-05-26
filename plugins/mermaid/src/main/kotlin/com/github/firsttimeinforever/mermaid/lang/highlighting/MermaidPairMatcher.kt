package com.github.firsttimeinforever.mermaid.lang.highlighting

import com.github.firsttimeinforever.mermaid.lang.MermaidLanguage
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens
import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class MermaidPairMatcher : PairedBraceMatcherAdapter(MyPairedBraceMatcher(), MermaidLanguage) {
  companion object {
    private class MyPairedBraceMatcher : PairedBraceMatcher {
      private val pairs = arrayOf(
        BracePair(MermaidTokens.OPEN_CURLY, MermaidTokens.CLOSE_CURLY, true),
        BracePair(MermaidTokens.OPEN_ROUND, MermaidTokens.CLOSE_ROUND, true),
        BracePair(MermaidTokens.OPEN_SQUARE, MermaidTokens.CLOSE_SQUARE, true),

        BracePair(MermaidTokens.Flowchart.STADIUM_START, MermaidTokens.Flowchart.STADIUM_END, true),
        BracePair(MermaidTokens.Flowchart.SUBROUTINE_START, MermaidTokens.Flowchart.SUBROUTINE_END, true),
        BracePair(MermaidTokens.Flowchart.CYLINDER_START, MermaidTokens.Flowchart.CYLINDER_END, true),
        BracePair(MermaidTokens.Flowchart.CIRCLE_START, MermaidTokens.Flowchart.CIRCLE_END, true),
        BracePair(MermaidTokens.Flowchart.ASYMMETRIC_START, MermaidTokens.CLOSE_SQUARE, true),
        BracePair(MermaidTokens.Flowchart.DIAMOND_START, MermaidTokens.Flowchart.DIAMOND_END, true),
        BracePair(MermaidTokens.Flowchart.HEXAGON_START, MermaidTokens.Flowchart.HEXAGON_END, true),
        BracePair(MermaidTokens.Flowchart.TRAP_START, MermaidTokens.Flowchart.INV_TRAP_END, true),
        BracePair(MermaidTokens.Flowchart.INV_TRAP_START, MermaidTokens.Flowchart.TRAP_END, true),
        BracePair(MermaidTokens.Flowchart.TRAP_START, MermaidTokens.Flowchart.TRAP_END, true),
        BracePair(MermaidTokens.Flowchart.INV_TRAP_START, MermaidTokens.Flowchart.INV_TRAP_END, true),
        BracePair(MermaidTokens.Flowchart.DOUBLE_CIRCLE_START, MermaidTokens.Flowchart.DOUBLE_CIRCLE_END, true),
        BracePair(MermaidTokens.Flowchart.SEP, MermaidTokens.Flowchart.SEP, true),

        BracePair(MermaidTokens.ANNOTATION_START, MermaidTokens.ANNOTATION_END, false),
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
