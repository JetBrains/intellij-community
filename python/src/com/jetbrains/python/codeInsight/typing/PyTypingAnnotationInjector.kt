// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typing

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.PyInjectionUtil
import com.jetbrains.python.codeInsight.PyInjectorBase
import com.jetbrains.python.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect
import com.jetbrains.python.codeInsight.typeHints.PyTypeHintDialect
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.regex.Pattern

/**
 * Injects fragments for type annotations either in string literals (quoted annotations containing forward references) or
 * in type comments starting with <tt># type:</tt>.
 *
 */
class PyTypingAnnotationInjector : PyInjectorBase() {
  override fun registerInjection(registrar: MultiHostRegistrar, context: PsiElement): PyInjectionUtil.InjectionResult? {
    // Handles only string literals containing quoted types
    val result = super.registerInjection(registrar, context)

    if (result === PyInjectionUtil.InjectionResult.EMPTY &&
        context is PsiComment &&
        context is PsiLanguageInjectionHost &&
        context.containingFile is PyFile
    ) {
      return registerCommentInjection(registrar, context as PsiLanguageInjectionHost)
    }
    return result
  }

  override fun getInjectedLanguage(context: PsiElement): Language? {
    if (context is PyStringLiteralExpression) {
      val typeEvalContext = TypeEvalContext.codeAnalysis(context.project, context.containingFile)
      if (isTypingLiteralArgument(context, typeEvalContext) || isTypingAnnotatedMetadataArgument(context, typeEvalContext)) {
        return null
      }
      if (PsiTreeUtil.getParentOfType(context, PyAnnotation::class.java, true, PyCallExpression::class.java) != null &&
          isTypingAnnotation(context.stringValue)
      ) {
        return PyTypeHintDialect.INSTANCE
      }
      if (isInsideValueOfExplicitTypeAnnotation(context)) {
        return PyTypeHintDialect.INSTANCE
      }
      if (isInsideNewStyleTypeVarBound(context)) {
        return PyTypeHintDialect.INSTANCE
      }
    }
    return null
  }

  companion object {
    val RE_TYPING_ANNOTATION: Pattern = Pattern.compile("\\s*\\S+(\\[.*])?\\s*")

    private fun isInsideValueOfExplicitTypeAnnotation(expr: PyStringLiteralExpression): Boolean {
      val assignment = PsiTreeUtil.getParentOfType(expr, PyAssignmentStatement::class.java)
      if (assignment == null || !PsiTreeUtil.isAncestor(assignment.assignedValue, expr, false)) {
        return false
      }
      return PyTypingTypeProvider.isExplicitTypeAlias(assignment, TypeEvalContext.codeAnalysis(expr.project, expr.containingFile))
    }

    private fun registerCommentInjection(
      registrar: MultiHostRegistrar,
      host: PsiLanguageInjectionHost,
    ): PyInjectionUtil.InjectionResult {
      val text = host.text
      val annotationText = PyTypingTypeProvider.getTypeCommentValue(text)
      if (annotationText != null) {
        val language: Language?
        if (PyTypingTypeProvider.TYPE_IGNORE_PATTERN.matcher(text).matches()) {
          language = null
        }
        else if (isFunctionTypeComment(host)) {
          language = PyFunctionTypeAnnotationDialect.INSTANCE
        }
        else {
          language = PyTypeHintDialect.INSTANCE
        }
        if (language != null) {
          registrar.startInjecting(language)
          registrar.addPlace("", "", host, PyTypingTypeProvider.getTypeCommentValueRange(text)!!)
          registrar.doneInjecting()
          return PyInjectionUtil.InjectionResult(true, true)
        }
      }
      return PyInjectionUtil.InjectionResult.EMPTY
    }

    private fun isTypingLiteralArgument(element: PsiElement, context: TypeEvalContext): Boolean {
      var parent = element.parent
      if (parent is PyTupleExpression) parent = parent.parent
      val subscription = parent as? PySubscriptionExpression ?: return false
      val operand = subscription.operand as? PyReferenceExpression ?: return false
      val resolvedNames = PyTypingTypeProvider.resolveToQualifiedNames(operand, context)
      return resolvedNames.any {
        PyTypingTypeProvider.LITERAL == it || PyTypingTypeProvider.LITERAL_EXT == it
      }
    }

    private fun isTypingAnnotatedMetadataArgument(
      element: PsiElement,
      context: TypeEvalContext,
    ): Boolean {
      val tuple = PyUtil.`as`(element.parent, PyTupleExpression::class.java)
      if (tuple == null) return false
      val parent = PyUtil.`as`(tuple.parent, PySubscriptionExpression::class.java)
      if (parent == null) return false

      val operand = parent.operand
      val resolvedNames = PyTypingTypeProvider.resolveToQualifiedNames(operand, context)
      if (resolvedNames.any { PyTypingTypeProvider.ANNOTATED == it || PyTypingTypeProvider.ANNOTATED_EXT == it }) {
        return tuple.elements[0] !== element
      }
      return false
    }

    private fun isFunctionTypeComment(comment: PsiElement): Boolean {
      val function = PsiTreeUtil.getParentOfType(comment, PyFunction::class.java)
      return function != null && function.typeComment === comment
    }

    private fun isInsideNewStyleTypeVarBound(element: PsiElement): Boolean {
      return PsiTreeUtil.getParentOfType(element, PyTypeParameter::class.java, true,
                                         PyTypeParameterListOwner::class.java) != null
    }

    private fun isTypingAnnotation(s: String): Boolean {
      return RE_TYPING_ANNOTATION.matcher(s).matches()
    }
  }
}
