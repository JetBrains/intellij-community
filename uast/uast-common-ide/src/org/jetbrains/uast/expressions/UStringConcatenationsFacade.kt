// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.expressions

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

/**
 * A helper class to work with string concatenations with variables and interpolated strings in a language-abstract way (UAST-based).
 * It is mostly useful for working with reference/language injections in cases when it is injected into multiple [PsiLanguageInjectionHost] joined together.
 *
 * @see PartiallyKnownString
 */
class UStringConcatenationsFacade private constructor(private val uContext: UExpression, val uastOperands: Sequence<UExpression>) {

  @Deprecated("use factory method `UStringConcatenationsFacade.createFromUExpression`",
              ReplaceWith("UStringConcatenationsFacade.createFromUExpression(uContext)"))
  @ApiStatus.Experimental
  constructor(uContext: UExpression) : this(uContext, buildLazyUastOperands(uContext, false) ?: emptySequence())

  @get:ApiStatus.Experimental
  val rootUExpression: UExpression
    get() = uContext

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
  fun asPartiallyKnownString() : PartiallyKnownString = PartiallyKnownString(segments.map { segment ->
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

    private fun buildLazyUastOperands(uContext: UExpression?, flatten: Boolean): Sequence<UExpression>? = when {
      uContext is UPolyadicExpression && isConcatenation(uContext) -> {
        val concatenationOperands = uContext.operands.asSequence()
        if (flatten)
          concatenationOperands.flatMap { operand -> buildLazyUastOperands(operand, true) ?: sequenceOf(operand) }
        else
          concatenationOperands
      }
      uContext is UInjectionHost && !isConcatenation(uContext.uastParent) -> {
        val host = uContext.psiLanguageInjectionHost
        if (!host.isValidHost) emptySequence() else sequenceOf(uContext)
      }
      else -> null
    }

    @JvmStatic
    @JvmOverloads
    fun createFromUExpression(uContext: UExpression?, flatten: Boolean = false): UStringConcatenationsFacade? {
      val operands = buildLazyUastOperands(uContext, flatten) ?: return null
      return UStringConcatenationsFacade(uContext!!, operands)
    }

    @JvmStatic
    fun createFromTopConcatenation(member: UExpression?): UStringConcatenationsFacade? {
      val topConcatenation = generateSequence(member?.uastParent, UElement::uastParent).takeWhile { isConcatenation(it) }
                               .lastOrNull() as? UExpression ?: member ?: return null

      val operands = buildLazyUastOperands(topConcatenation, true) ?: return null
      return UStringConcatenationsFacade(topConcatenation, operands)
    }

    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    @Deprecated("doesn't support concatenation for Kotlin", ReplaceWith("createFromUExpression"))
    fun create(context: PsiElement?): UStringConcatenationsFacade? {
      if (context == null || context !is PsiLanguageInjectionHost && context.firstChild !is PsiLanguageInjectionHost) {
        return null
      }
      val uElement = context.toUElementOfType<UExpression>() ?: return null
      return createFromUExpression(uElement)
    }

    @JvmStatic
    fun getConcatenationsFacade(context: PsiElement): UStringConcatenationsFacade? {
      val uElement = context.toUElementOfExpectedTypes(UInjectionHost::class.java, UPolyadicExpression::class.java) ?: return null
      if (uElement.sourcePsi !== context) return null
      if (isConcatenation(uElement.uastParent)) return null
      return if (uElement is UInjectionHost) createFromUExpression(uElement, false) else createFromUExpression(uElement, true)
    }
  }

}