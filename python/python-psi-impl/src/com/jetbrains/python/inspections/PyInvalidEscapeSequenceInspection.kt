package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.quickfix.PyConvertToRawStringQuickFix
import com.jetbrains.python.inspections.quickfix.PyEscapeBackslashQuickFix
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyStringElement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.PyStringLiteralDecoder
import com.jetbrains.python.psi.types.TypeEvalContext

class PyInvalidEscapeSequenceInspection : PyInspection() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = Visitor(holder, PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder?, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) =
      node.stringElements.forEach { processString(it, node) }

    private fun processString(element: PyStringElement, node: PyStringLiteralExpression) {
      if (element.prefix.contains("r", ignoreCase = true)) return

      val content = element.contentRange.substring(element.text)
      val offsetInNode = element.textRange.startOffset + element.contentRange.startOffset - node.textRange.startOffset

      val fstringRanges = if (element is PyFormattedStringElement) {
        element.literalPartRanges.asSequence().map { it.shiftLeft(element.contentRange.startOffset) }
      }
      else {
        sequenceOf(TextRange(0, content.length))
      }

      val validEscapes = fstringRanges.flatMap { range ->
        PyStringLiteralDecoder.PATTERN_ESCAPE.toRegex()
          .findAll(content, range.startOffset)
          .takeWhile { it.range.last < range.endOffset }
          .map { it.range }
      }
        .iterator()
      val hasValidEscapes = validEscapes.hasNext()
      var curValidEscape = if (hasValidEscapes) validEscapes.next() else null

      for (range in fstringRanges) {
        var charIndex = range.startOffset
        while (charIndex < range.endOffset - 1) {

          if (content[charIndex] != '\\') {
            charIndex++
            continue
          }

          while (curValidEscape?.let { it.last < charIndex } == true) {
            curValidEscape = if (validEscapes.hasNext()) validEscapes.next() else null
          }
          if (curValidEscape?.contains(charIndex) == true) {
            charIndex = curValidEscape.last + 1
            continue
          }

          val problemOffset = offsetInNode + charIndex

          val fixes = LocalQuickFix.notNullElements(
            PyEscapeBackslashQuickFix(problemOffset),
            if (!hasValidEscapes) PyConvertToRawStringQuickFix(problemOffset) else null
          )

          registerProblem(
            node,
            PyPsiBundle.message("INSP.invalid.escape.sequence", "\\${content[charIndex + 1]}"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            null,
            TextRange(problemOffset, problemOffset + 2),
            *fixes
          )
          charIndex += 2
        }
      }
    }
  }
}
