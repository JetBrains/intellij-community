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
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveUtil

// TODO: consider merging with `PythonInlayParameterHintsProvider`
/**
 * Provides inlay hints for tuple elements in @pytest.mark.parametrize decorators,
 * showing which parameter each value corresponds to.
 */
class PyTestParametrizeInlayHintsProvider : InlayHintsProvider {

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector()

  private class Collector : SharedBypassCollector {
    private val hintFormat = HintFormat.default
      .withFontSize(HintFontSize.ABitSmallerThanInEditor)

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
        matchValuesToParameters(values, parameterNames).forEach { (expr, name) ->
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
     * Pairs each value with its parameter.
     *
     * A starred value, such as `*get_ids()`, stands for an unknown count of values and gets no hint.
     * The values before the first starred value match the first parameters.
     * The values after the last starred value match the last parameters.
     * The values between two starred values get no hint.
     * When the leading and the trailing values outnumber the parameters, the trailing values get no hint.
     */
    private fun matchValuesToParameters(values: List<PyExpression>, parameterNames: List<String>): List<Pair<PyExpression, String>> {
      val firstStarred = values.indexOfFirst { it.isStarred }
      if (firstStarred < 0) return values.zip(parameterNames)

      val leading = values.subList(0, firstStarred).zip(parameterNames)
      val trailingValues = values.subList(values.indexOfLast { it.isStarred } + 1, values.size)
      if (firstStarred + trailingValues.size > parameterNames.size) return leading
      return leading + trailingValues.zip(parameterNames.takeLast(trailingValues.size))
    }

    private val PyExpression.isStarred: Boolean
      get() = this is PyStarExpression || this is PyStarArgument

    private val PyDecorator.parameterNames: List<String>?
      get() {
        val stringValue =
          (arguments.firstOrNull() as? PyStringLiteralExpression)?.stringValue ?: return null

        return stringValue.split(',').map { it.trim() }.filterNot { it.isBlank() }
      }
  }
}

private const val paramName = "pytest.param"
private const val parametrizePublicName = "pytest.mark.parametrize"
private const val trueParametrizeName = "_pytest.mark.structures.MarkGenerator.parametrize"
