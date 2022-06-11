// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PlatformIcons
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.*

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
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val originalElement = parameters.originalPosition?.parent ?: return

    val possibleSequenceExpr = if (originalElement is PyStringLiteralExpression) originalElement.parent else originalElement
    if (possibleSequenceExpr is PyDictLiteralExpression || possibleSequenceExpr is PySetLiteralExpression) { // it's set literal when user is typing the first key
      addCompletionToCallExpression(originalElement, possibleSequenceExpr as PyTypedElement, result)
      addCompletionToAssignment(originalElement, possibleSequenceExpr, result)
      addCompletionToReturnStatement(originalElement, possibleSequenceExpr, result)
    }
  }

  private fun addCompletionToCallExpression(originalElement: PsiElement,
                                            possibleSequenceExpr: PyTypedElement,
                                            result: CompletionResultSet) {
    val callExpression = PsiTreeUtil.getParentOfType(originalElement, PyCallExpression::class.java)
    if (callExpression != null) {
      val typeEvalContext = TypeEvalContext.codeCompletion(originalElement.project, originalElement.containingFile)
      val callType = typeEvalContext.getType(callExpression.callee ?: return)
      if (callType !is PyCallableType) return

      val argumentIndex = PyPsiUtils.findArgumentIndex(callExpression, possibleSequenceExpr)
      if (argumentIndex < 0) return
      val params = callType.getParameters(typeEvalContext) ?: return
      if (params.size < argumentIndex) return
      val expectedType = params[argumentIndex].getType(typeEvalContext)
      val actualType = typeEvalContext.getType(possibleSequenceExpr)
      addCompletionForTypedDictKeys(expectedType, actualType, result, originalElement !is PyStringLiteralExpression)
    }
  }

  private fun addCompletionToAssignment(originalElement: PsiElement,
                                        possibleSequenceExpr: PyTypedElement,
                                        result: CompletionResultSet) {
    val assignment = PsiTreeUtil.getParentOfType(originalElement, PyAssignmentStatement::class.java)
    if (assignment != null) {
      val typeEvalContext = TypeEvalContext.codeCompletion(originalElement.project, originalElement.containingFile)
      val targetToValue = if (assignment.targets.size == 1) assignment.targets[0] to assignment.assignedValue
      else assignment.targetsToValuesMapping.firstOrNull { it.second == possibleSequenceExpr }?.let { it.first to it.second }

      if (targetToValue != null) {
        val expectedType = typeEvalContext.getType(targetToValue.first as PyTypedElement)
        val actualType = typeEvalContext.getType(targetToValue.second as PyTypedElement)
        addCompletionForTypedDictKeys(expectedType, actualType, result, originalElement !is PyStringLiteralExpression)
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
            val actualType = typeEvalContext.getType(element)
            addCompletionForTypedDictKeys(expectedType, actualType, result, originalElement !is PyStringLiteralExpression)
          }
        }
      }
    }
  }

  private fun addCompletionToReturnStatement(originalElement: PsiElement,
                                             possibleSequenceExpr: PyTypedElement,
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
          val actualType = typeEvalContext.getType(possibleSequenceExpr)
          addCompletionForTypedDictKeys(expectedType, actualType, result, originalElement !is PyStringLiteralExpression)
        }
      }
    }
  }

  private fun addCompletionForTypedDictKeys(expectedType: PyType?,
                                            actualType: PyType?,
                                            dictCompletion: CompletionResultSet,
                                            addQuotes: Boolean) {
    if (expectedType is PyTypedDictType) {
      val keys =
        when {
          actualType is PyTypedDictType -> expectedType.fields.keys.filterNot { it in actualType.fields }
          actualType is PyClassType && PyNames.SET == actualType.name -> expectedType.fields.keys
          else -> return
        }

      for (key in keys) {
        dictCompletion.addElement(
          LookupElementBuilder
            .create(if (addQuotes) "'$key'" else key)
            .withTypeText("dict key")
            .withIcon(PlatformIcons.PARAMETER_ICON)
        )
      }
    }
  }
}
