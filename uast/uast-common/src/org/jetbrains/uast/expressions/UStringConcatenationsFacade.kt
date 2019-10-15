// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.expressions

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*


class UStringConcatenationsFacade @ApiStatus.Experimental constructor(uContext: UExpression) {

  val uastOperands: Sequence<UExpression> = run {
    when {
      uContext is UPolyadicExpression -> uContext.operands.asSequence()
      uContext is ULiteralExpression && uContext.uastParent !is UPolyadicExpression -> {
        val host = uContext.sourceInjectionHost
        if (host == null || !host.isValidHost) emptySequence() else sequenceOf(uContext)
      }
      else -> emptySequence()
    }
  }

  val psiLanguageInjectionHosts: Sequence<PsiLanguageInjectionHost> =
    uastOperands.mapNotNull { (it as? ULiteralExpression)?.psiLanguageInjectionHost }.distinct()

  /**
   * external (non string-literal) expressions in string interpolations and/or concatenations
   */
  val placeholders: List<Pair<TextRange, String>>
    get() = segments.mapNotNull { segment ->
      when (segment) {
        is Segment.Placeholder -> segment.range to (segment.value ?: "missingValue")
        else -> null
      }
    }

  private sealed class Segment {
    abstract val value: String?
    abstract val range: TextRange
    abstract val uExpression: UExpression

    class StringLiteral(override val value: String, override val range: TextRange, override val uExpression: ULiteralExpression) : Segment()
    class Placeholder(override val value: String?, override val range: TextRange, override val uExpression: UExpression) : Segment()
  }

  private val segments: List<Segment> by lazy(LazyThreadSafetyMode.NONE) {
    val bounds = uContext.sourcePsi?.textRange ?: return@lazy emptyList<Segment>()
    val operandsList = uastOperands.toList()
    ArrayList<Segment>(operandsList.size).apply {
      for (i in operandsList.indices) {
        val operand = operandsList[i]
        val sourcePsi = operand.sourcePsi ?: continue
        val selfRange = sourcePsi.textRange
        if (operand is ULiteralExpression)
          add(Segment.StringLiteral(operand.evaluateString() ?: "", selfRange, operand))
        else {
          val prev = operandsList.getOrNull(i - 1)
          val next = operandsList.getOrNull(i + 1)
          val start = when (prev) {
            null -> bounds.startOffset
            is ULiteralExpression -> prev.sourcePsi?.textRange?.endOffset ?: selfRange.startOffset
            else -> selfRange.startOffset
          }
          val end = when (next) {
            null -> bounds.endOffset
            is ULiteralExpression -> next.sourcePsi?.textRange?.startOffset ?: selfRange.endOffset
            else -> selfRange.endOffset
          }
          val range = TextRange.create(start, end)
          val evaluate = operand.evaluateString()

          add(Segment.Placeholder(evaluate, range, operand))
        }
      }
    }
  }

  @ApiStatus.Experimental
  fun asPartiallyKnownString() = PartiallyKnownString(segments.map { segment ->
    segment.value?.let { value ->
      StringEntry.Known(value, segment.uExpression.sourcePsi, getSegmentInnerTextRange(segment))
    } ?: StringEntry.Unknown(segment.uExpression.sourcePsi, getSegmentInnerTextRange(segment))
  })

  private fun getSegmentInnerTextRange(segment: Segment): TextRange {
    val sourcePsi = segment.uExpression.sourcePsi ?: throw IllegalStateException("no sourcePsi for $segment")
    val sourcePsiTextRange = sourcePsi.textRange
    val range = segment.range
    if (range.startOffset > sourcePsiTextRange.startOffset)
      return range.shiftLeft(sourcePsiTextRange.startOffset)
    return ElementManipulators.getValueTextRange(sourcePsi)
  }

  companion object {
    @JvmStatic
    fun create(context: PsiElement): UStringConcatenationsFacade? {
      if (context !is PsiLanguageInjectionHost && context.firstChild !is PsiLanguageInjectionHost) {
        return null
      }
      val uElement = context.toUElement(UExpression::class.java) ?: return null
      return UStringConcatenationsFacade(uElement)
    }
  }

}

