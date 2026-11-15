// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.TypeEvalContext

// TODO: consider merging with `PythonInlayParameterHintsProvider`
/**
 * Provides inlay hints for tuple elements in @pytest.mark.parametrize decorators,
 * showing which parameter each value corresponds to.
 */
class PyTestParametrizeInlayHintsProvider : InlayHintsProvider {

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector(file)

  private class Collector(private val file: PsiFile) : SharedBypassCollector {
    private val hintFormat = HintFormat.default
      .withFontSize(HintFontSize.ABitSmallerThanInEditor)

    private val context by lazy { TypeEvalContext.codeAnalysis(file.project, file) }

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      if (element !is PyDecorator) return

      val callee = element.callee as? PyReferenceExpression ?: return

      // NOTE: we can't resolve the true origin using code analysis because part of the chain has an inferred type
      val resolvedQNames = PyResolveUtil.resolveImportedElementQNameLocally(callee)
      if (resolvedQNames.none { it.toString() == parametrizePublicName }) return

      val parameterNames = element.parameterNames ?: return

      val valueSets = element.arguments.getOrNull(1)?.let(PyPsiUtils::flattenParens) as? PySequenceExpression ?: return

      for (valueSet in valueSets.elements) {
        val values = when (val valueSet = PyPsiUtils.flattenParens(valueSet)) {
          is PyCallExpression
            if (valueSet.callee as? PyReferenceExpression)
              ?.let(PyResolveUtil::resolveImportedElementQNameLocally)
              ?.any { it.toString() == paramName } == true
            -> valueSet.arguments.filterNot { it is PyKeywordArgument || it is PyStarArgument && it.isKeyword }
          is PyTupleExpression -> valueSet.elements.toList()
          else -> continue
        }
        matchValuesToParameters(expandSpreads(values), parameterNames).forEach { (expr, name) ->
          sink.addPresentation(
            position = InlineInlayPosition(expr.textRange.startOffset, false),
            hintFormat = hintFormat,
          ) {
            text(name)
          }
        }
      }
    }

    /**
     * Gives the positions that [values] fill.
     *
     * A spread of a tuple or a list display, such as `*(1, 2)`, gives the positions of its elements.
     * A spread inside such a display expands in the same way.
     * A spread of a fixed-length tuple type gives one position for each element. These positions get no hint.
     * Every other spread, such as `*get_ids()`, has an unknown length.
     */
    private fun expandSpreads(values: List<PyExpression>): List<Slot> = values.flatMap { value ->
      if (value !is PyStarExpression && value !is PyStarArgument) return@flatMap listOf(Slot.Value(value))
      when (val operand = PyPsiUtils.flattenParens(PsiTreeUtil.getChildOfType(value, PyExpression::class.java))) {
        is PyTupleExpression -> expandSpreads(operand.elements.toList())
        is PyListLiteralExpression -> expandSpreads(operand.elements.toList())
        null -> listOf(Slot.UnknownSpread)
        else -> {
          val length = PyCallExpressionHelper.knownFixedTupleSpreadLength(operand, context)
          if (length >= 0) List(length) { Slot.FixedSpread } else listOf(Slot.UnknownSpread)
        }
      }
    }

    /**
     * Pairs each value with its parameter.
     *
     * A spread of unknown length stands for an unknown count of values and gets no hint.
     * The values before the first such spread match the first parameters.
     * The values after the last such spread match the last parameters.
     * The values between two such spreads get no hint.
     * When the leading and the trailing values outnumber the parameters, the trailing values get no hint.
     */
    private fun matchValuesToParameters(slots: List<Slot>, parameterNames: List<String>): List<Pair<PyExpression, String>> {
      val firstUnknown = slots.indexOfFirst { it.hasUnknownLength }
      val pairs = if (firstUnknown < 0) {
        slots.zip(parameterNames)
      }
      else {
        val leading = slots.subList(0, firstUnknown).zip(parameterNames)
        val trailing = slots.subList(slots.indexOfLast { it.hasUnknownLength } + 1, slots.size)
        if (firstUnknown + trailing.size > parameterNames.size) leading
        else leading + trailing.zip(parameterNames.takeLast(trailing.size))
      }
      return pairs.mapNotNull { (slot, name) ->
        when (slot) {
          is Slot.Value -> slot.value to name
          Slot.FixedSpread, Slot.UnknownSpread -> null
        }
      }
    }

    private val Slot.hasUnknownLength: Boolean
      get() = when (this) {
        is Slot.Value, Slot.FixedSpread -> false
        Slot.UnknownSpread -> true
      }

    private val PyDecorator.parameterNames: List<String>?
      get() {
        val stringValue =
          (arguments.firstOrNull() as? PyStringLiteralExpression)?.stringValue ?: return null

        return stringValue.split(',').map { it.trim() }.filterNot { it.isBlank() }
      }
  }
}

/** One item in the values of a parametrize case. */
private sealed interface Slot {
  /** A position that [value] fills. */
  class Value(val value: PyExpression) : Slot

  /** A position that a spread of a fixed-length tuple fills. */
  data object FixedSpread : Slot

  /** A spread of unknown length. */
  data object UnknownSpread : Slot
}

private const val paramName = "pytest.param"
private const val parametrizePublicName = "pytest.mark.parametrize"
private const val trueParametrizeName = "_pytest.mark.structures.MarkGenerator.parametrize"
