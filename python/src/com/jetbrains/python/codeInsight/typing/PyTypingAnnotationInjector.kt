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
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.PyTypeParameterListOwner
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.stubs.PyTypingAliasStubType
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Injects fragments for type annotations either in string literals (quoted annotations containing forward references) or
 * in type comments starting with <tt># type:</tt>.
 *
 */
class PyTypingAnnotationInjector : PyInjectorBase() {
  override fun registerInjection(registrar: MultiHostRegistrar, context: PsiElement): PyInjectionUtil.InjectionResult? {
    // Handles only string literals containing quoted types
    getInjectedLanguage(context)?.let { language ->
      val element = PyInjectionUtil.getLargestStringLiteral(context)
      if (element != null) {
        return if (language === PyTypeHintDialect.INSTANCE && "\n" in element.text) {
          PyInjectionUtil.registerStringLiteralInjectionWithParenthesis(element, registrar, language)
        }
        else {
          PyInjectionUtil.registerStringLiteralInjection(element, registrar, language)
        }
      }
    }

    if (context is PsiComment &&
        context is PsiLanguageInjectionHost &&
        context.containingFile is PyFile
    ) {
      return registerCommentInjection(registrar, context)
    }
    return PyInjectionUtil.InjectionResult.EMPTY
  }

  override fun getInjectedLanguage(context: PsiElement): Language? {
    if (context is PyStringLiteralExpression) {
      if (isTypingLiteralArgument(context) || isTypingAnnotatedMetadataArgument(context)) {
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
      if (isTypingCastTypeArgument(context)) {
        return PyTypeHintDialect.INSTANCE
      }
      if (isTypingTypeVarTypeArgument(context)) {
        return PyTypeHintDialect.INSTANCE
      }
      if (isTypingNewTypeTypeArgument(context)) {
        return PyTypeHintDialect.INSTANCE
      }
      if (isTypingAssertTypeTypeArgument(context)) {
        return PyTypeHintDialect.INSTANCE
      }
    }
    return null
  }

  private fun isTypingCastTypeArgument(
    expr: PyStringLiteralExpression,
  ): Boolean {
    val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java) ?: return false
    val call = argList.getParent() as? PyCallExpression ?: return false
    val callee = call.callee as? PyReferenceExpression ?: return false

    val resolvedNames = resolveLocally(callee)
    if (PyTypingTypeProvider.CAST !in resolvedNames && PyTypingTypeProvider.CAST_EXT !in resolvedNames) {
      return false
    }

    val args = argList.arguments
    if (args.isEmpty()) return false
    argList.getKeywordArgument("typ")?.let {
      return PsiTreeUtil.isAncestor(it, expr, false)
    }

    val firstArg = args[0]
    return PsiTreeUtil.isAncestor(firstArg, expr, false)
  }

  private fun isTypingTypeVarTypeArgument(
    expr: PyStringLiteralExpression,
  ): Boolean {
    val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java) ?: return false
    val call = argList.getParent() as? PyCallExpression ?: return false
    val callee = call.callee as? PyReferenceExpression ?: return false

    val resolvedNames = resolveLocally(callee)
    if (PyTypingTypeProvider.TYPE_VAR !in resolvedNames && PyTypingTypeProvider.TYPE_VAR_EXT !in resolvedNames) {
      return false
    }

    // Positional constraints start from the second argument (index 1), the first one is the TypeVar name
    for (arg in argList.arguments.drop(1)) {
      if (arg is PyKeywordArgument) break
      if (PsiTreeUtil.isAncestor(arg, expr, false)) return true
    }

    // Keyword 'bound'
    val boundKw = argList.getKeywordArgument("bound")
    if (boundKw != null) {
      val value = boundKw.valueExpression
      if (value != null && PsiTreeUtil.isAncestor(value, expr, false)) return true
    }

    // Keyword 'default' (PEP 696)
    val defaultKw = argList.getKeywordArgument("default")
    if (defaultKw != null) {
      val value = defaultKw.valueExpression
      if (value != null && PsiTreeUtil.isAncestor(value, expr, false)) return true
    }

    return false
  }

  private fun isTypingNewTypeTypeArgument(
    expr: PyStringLiteralExpression,
  ): Boolean {
    val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java) ?: return false
    val call = argList.getParent() as? PyCallExpression ?: return false
    val callee = call.callee as? PyReferenceExpression ?: return false

    val resolvedNames = resolveLocally(callee)
    if (PyTypingTypeProvider.NEW_TYPE !in resolvedNames) {
      return false
    }

    val args = argList.arguments
    if (args.size < 2) return false

    val secondArg = args[1]
    return PsiTreeUtil.isAncestor(secondArg, expr, false)
  }

  private fun isTypingAssertTypeTypeArgument(
    expr: PyStringLiteralExpression,
  ): Boolean {
    val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java) ?: return false
    val call = argList.parent as? PyCallExpression ?: return false
    val callee = call.callee as? PyReferenceExpression ?: return false

    val resolvedNames = resolveLocally(callee)
    if (PyTypingTypeProvider.ASSERT_TYPE !in resolvedNames) {
      return false
    }

    val args = argList.arguments
    if (args.size < 2) return false

    val expectedTypeArg = args[1]
    return PsiTreeUtil.isAncestor(expectedTypeArg, expr, false)
  }
}

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

private fun isTypingLiteralArgument(element: PsiElement): Boolean {
  var parent = element.parent
  if (parent is PyTupleExpression) parent = parent.parent
  val subscription = parent as? PySubscriptionExpression ?: return false
  val operand = subscription.operand as? PyReferenceExpression ?: return false
  val resolvedNames = resolveLocally(operand)
  return resolvedNames.any {
    PyTypingTypeProvider.LITERAL == it || PyTypingTypeProvider.LITERAL_EXT == it
  }
}

private fun resolveLocally(operand: PyReferenceExpression): List<String> =
  PyResolveUtil.resolveImportedElementQNameLocally(operand).map { it.toString() }

private fun isTypingAnnotatedMetadataArgument(
  element: PsiElement,
): Boolean {
  val tuple = PyUtil.`as`(element.parent, PyTupleExpression::class.java)
  if (tuple == null) return false
  val parent = PyUtil.`as`(tuple.parent, PySubscriptionExpression::class.java)
  if (parent == null) return false

  val operand = parent.operand as? PyReferenceExpression ?: return false
  val resolvedNames = resolveLocally(operand)
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
  return PyTypingAliasStubType.RE_TYPE_HINT_LIKE_STRING.toRegex() matches s
}