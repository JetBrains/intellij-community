// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.IconManager
import com.intellij.util.ProcessingContext
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypedDictType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Provides completion variants for keys of dict literals marked as TypedDict
 * @see PyTypedDictType
 */
class PyDictLiteralCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement().inside(PySequenceExpression::class.java), DictLiteralCompletionProvider())
  }
}

private class DictLiteralCompletionProvider : CompletionProvider<CompletionParameters?>() {

  private val DEFAULT_QUOTE = "\""

  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val originalElement = parameters.originalPosition?.parent ?: return

    var possibleSequenceExpr = if (originalElement is PyStringLiteralExpression) originalElement.parent else originalElement
    if (possibleSequenceExpr is PyKeyValueExpression) {
      possibleSequenceExpr = possibleSequenceExpr.parent
    }
    if (possibleSequenceExpr is PyDictLiteralExpression || possibleSequenceExpr is PySetLiteralExpression) { // it's set literal when user is typing the first key
      addCompletionToCallExpression(originalElement, possibleSequenceExpr as PySequenceExpression, result)
      addCompletionToAssignment(originalElement, possibleSequenceExpr, result)
      addCompletionToReturnStatement(originalElement, possibleSequenceExpr, result)
    }
  }

  private fun addCompletionToCallExpression(originalElement: PsiElement,
                                            possibleSequenceExpr: PySequenceExpression,
                                            result: CompletionResultSet) {
    val typeEvalContext = TypeEvalContext.codeCompletion(originalElement.project, originalElement.containingFile)
    val quote = getForcedQuote(possibleSequenceExpr, originalElement)
    PyCallExpressionHelper.getMappedParameters(possibleSequenceExpr, PyResolveContext.defaultContext(typeEvalContext))?.forEach {
      addCompletionForTypedDictKeys(it.getType(typeEvalContext), possibleSequenceExpr, result, quote)
    }
  }

  private fun getForcedQuote(possibleSequenceExpr: PySequenceExpression, originalElement: PsiElement): String {
    if (originalElement !is PyStringLiteralExpression) {
      val usedQuotes = possibleSequenceExpr.elements.mapNotNull { if (it is PyKeyValueExpression) it.key else it }
        .filterIsInstance<PyStringLiteralExpression>()
        .flatMap { it.stringElements }
        .map { it.quote }
        .toSet()
      return usedQuotes.singleOrNull() ?: DEFAULT_QUOTE
    }
    return ""
  }

  private fun addCompletionToAssignment(originalElement: PsiElement,
                                        possibleSequenceExpr: PySequenceExpression,
                                        result: CompletionResultSet) {
    val assignment = PsiTreeUtil.getParentOfType(originalElement, PyAssignmentStatement::class.java)
    if (assignment != null) {
      val typeEvalContext = TypeEvalContext.codeCompletion(originalElement.project, originalElement.containingFile)
      val targetToValue = if (assignment.targets.size == 1) assignment.targets[0] to assignment.assignedValue
      else assignment.targetsToValuesMapping.firstOrNull { it.second == possibleSequenceExpr }?.let { it.first to it.second }

      if (targetToValue?.first != null && targetToValue.second != null) {
        val expectedType = typeEvalContext.getType(targetToValue.first!!)
        addCompletionForTypedDictKeys(expectedType, targetToValue.second!!, result, getForcedQuote(possibleSequenceExpr, originalElement))
      }
      else { //multiple target expressions and there is a PsiErrorElement
        val targetExpr = assignment.assignedValue
        val element = if (targetExpr is PyTupleExpression) targetExpr.elements.firstOrNull {
          it == possibleSequenceExpr
        }
        else return
        if (element != null) {
          val index = targetExpr.elements.indexOf(element)
          if (index < assignment.targets.size) {
            val expectedType = typeEvalContext.getType(assignment.targets[index])
            addCompletionForTypedDictKeys(expectedType, element, result, getForcedQuote(possibleSequenceExpr, originalElement))
          }
        }
      }
    }
  }

  private fun addCompletionToReturnStatement(originalElement: PsiElement,
                                             possibleSequenceExpr: PySequenceExpression,
                                             result: CompletionResultSet) {
    val returnStatement = PsiTreeUtil.getParentOfType(originalElement, PyReturnStatement::class.java)
    if (returnStatement != null) {
      val typeEvalContext = TypeEvalContext.codeCompletion(originalElement.project, originalElement.containingFile)
      val owner = ScopeUtil.getScopeOwner(returnStatement)
      if (owner is PyFunction) {
        val annotation = owner.annotation
        val typeCommentAnnotation = owner.typeCommentAnnotation
        if (annotation != null || typeCommentAnnotation != null) { // to ensure that we have return type specified, not inferred
          val expectedType = typeEvalContext.getReturnType(owner)
          addCompletionForTypedDictKeys(expectedType, possibleSequenceExpr, result, getForcedQuote(possibleSequenceExpr, originalElement))
        }
      }
    }
  }

  private fun addCompletionForTypedDictKeys(expectedType: PyType?,
                                            expression: PyExpression,
                                            dictCompletion: CompletionResultSet,
                                            quote: String) {
    if (expectedType is PyTypedDictType) {
      val keys = when (expression) {
        is PyDictLiteralExpression -> expression.elements
          .map { it.key }
          .filterIsInstance<PyStringLiteralExpression>()
          .map { it.stringValue }
        is PySetLiteralExpression -> emptyList()
        else -> return
      }
      for (key in expectedType.fields.keys.filterNot { it in keys }) {
        dictCompletion.addElement(
          MLRankingIgnorable.wrap(
            LookupElementBuilder
              .create("$quote$key$quote")
              .withTypeText("dict key")
              .withIcon(IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Parameter))
          )
        )
      }
    }
  }
}
