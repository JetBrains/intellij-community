package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.types.PyExpectedTypeJudgement
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Provides literal type variants in the following cases:
 * ```python
 * x: Literal["foo", "bar"]
 * x = <caret>
 * x = fo<caret>
 * x = "fo<caret>"
 * ```
 *
 * or
 *
 * ```python
 * def f(x: Literal["foo", "bar"]): ...
 * f(<caret>)
 * f(fo<caret>)
 * f("fo<caret>")
 * ```
 */
class PyLiteralTypeCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement(), PyLiteralTypeCompletionProvider())
  }
}

private class PyLiteralTypeCompletionProvider : CompletionProvider<CompletionParameters?>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val position = parameters.position.parent as? PyExpression ?: return
    if (!(position is PyStringLiteralExpression || position is PyReferenceExpression && !position.isQualified)) return

    val typeEvalContext = TypeEvalContext.codeCompletion(position.project, position.containingFile)
    val expectedType = PyExpectedTypeJudgement.getExpectedType(position, typeEvalContext)
    addToResult(position, listOfNotNull(expectedType), result)
  }

  private fun addToResult(position: PyExpression, possibleTypes: List<PyType>, result: CompletionResultSet) {
    val useStringValue = position is PyStringLiteralExpression

    possibleTypes.asSequence()
      .flatMap { it.toStream() }
      .filterIsInstance<PyLiteralType>()
      .filter { it.stringValue != null } // Take only string literals
      .mapNotNull { if (useStringValue) it.stringValue else it.expressionText }
      .forEach { lookupString ->
        result.addElement(
          MLRankingIgnorable.wrap(
            PrioritizedLookupElement.withPriority(
              LookupElementBuilder
                .create(lookupString)
                .withIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Parameter)),
              PythonCompletionWeigher.PRIORITY_WEIGHT.toDouble()
            )
          )
        )
      }
  }
}