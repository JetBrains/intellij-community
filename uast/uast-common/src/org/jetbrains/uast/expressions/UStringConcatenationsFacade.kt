// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.expressions

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.SmartList
import com.intellij.util.containers.toHeadAndTail
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
    segment.value?.let { value -> StringEntry.Known(value, segment.uExpression) } ?: StringEntry.Unknown(segment.uExpression)
  })


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

@ApiStatus.Experimental
sealed class StringEntry {
  abstract val uExpression: UExpression

  class Known(val value: String, override val uExpression: UExpression) : StringEntry()
  class Unknown(override val uExpression: UExpression) : StringEntry()
}

@ApiStatus.Experimental
class PartiallyKnownString(val segments: List<StringEntry>) {

  val valueIfKnown: String?
    get() {
      (segments.singleOrNull() as? StringEntry.Known)?.let { return it.value }

      val stringBuffer = StringBuffer()
      for (segment in segments) {
        when (segment) {
          is StringEntry.Known -> stringBuffer.append(segment.value)
          is StringEntry.Unknown -> return null
        }
      }
      return stringBuffer.toString()
    }

  override fun toString(): String = segments.joinToString { segment ->
    when (segment) {
      is StringEntry.Known -> segment.value
      is StringEntry.Unknown -> "<???>"
    }
  }

  constructor(single: StringEntry) : this(listOf(single))

  constructor(string: String, uExpression: UExpression) : this(StringEntry.Known(string, uExpression))

  fun findIndexOfInKnown(pattern: String): Int {
    var accumulated = 0
    for (segment in segments) {
      when (segment) {
        is StringEntry.Known -> {
          val i = segment.value.indexOf(pattern)
          if (i >= 0) return accumulated + i
          accumulated += segment.value.length
        }
        is StringEntry.Unknown -> {
        }
      }
    }
    return -1
  }

  fun splitAtInKnown(splitAt: Int): Pair<PartiallyKnownString, PartiallyKnownString> {
    var accumulated = 0
    val left = SmartList<StringEntry>()
    for ((i, segment) in segments.withIndex()) {
      when (segment) {
        is StringEntry.Known -> {
          if (accumulated + segment.value.length < splitAt) {
            accumulated += segment.value.length
            left.add(segment)
          }
          else {
            val leftPart = segment.value.substring(0, splitAt - accumulated)
            val rightPart = segment.value.substring(splitAt - accumulated)
            left.add(StringEntry.Known(leftPart, segment.uExpression))

            return PartiallyKnownString(left) to PartiallyKnownString(
              ArrayList<StringEntry>(segments.lastIndex - i + 1).apply {
                if (rightPart.isNotEmpty())
                  add(StringEntry.Known(rightPart, segment.uExpression))
                addAll(segments.subList(i, segments.size))
              }
            )
          }
        }
        is StringEntry.Unknown -> {
          left.add(segment)
        }
      }
    }
    return this to PartiallyKnownString.empty
  }

  fun split(pattern: String): List<PartiallyKnownString> {

    tailrec fun collectPaths(result: MutableList<PartiallyKnownString>,
                             pending: MutableList<StringEntry>,
                             segments: List<StringEntry>): MutableList<PartiallyKnownString> {

      val (head, tail) = segments.toHeadAndTail() ?: return result.apply { add(PartiallyKnownString(pending)) }

      when (head) {
        is StringEntry.Unknown -> return collectPaths(result, pending.apply { add(head) }, tail)
        is StringEntry.Known -> {
          val value = head.value

          val stringPaths = value.split(pattern)
          if (stringPaths.size == 1) {
            return collectPaths(result, pending.apply { add(head) }, tail)
          }
          else {
            return collectPaths(
              result.apply {
                add(PartiallyKnownString(pending.apply { add(StringEntry.Known(stringPaths.first(), head.uExpression)) }))
                addAll(stringPaths.subList(1, stringPaths.size - 1).map { PartiallyKnownString(it, head.uExpression) })
              },
              mutableListOf(StringEntry.Known(stringPaths.last(), head.uExpression)),
              tail
            )
          }

        }
      }

    }

    return collectPaths(SmartList(), mutableListOf(), segments)

  }

  companion object {
    val empty = PartiallyKnownString(emptyList())
  }

}

