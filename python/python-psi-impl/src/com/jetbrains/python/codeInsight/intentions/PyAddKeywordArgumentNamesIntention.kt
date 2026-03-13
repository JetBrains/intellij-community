// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext

internal class PyAddKeywordArgumentNamesIntention : PsiUpdateModCommandAction<PyExpression>(PyExpression::class.java) {

  override fun getFamilyName(): String = PyPsiBundle.message("INTN.NAME.add.keyword.argument.names")

  override fun isElementApplicable(element: PyExpression, context: ActionContext): Boolean {
    return element.parent is PyArgumentList
  }

  override fun stopSearchAt(element: PsiElement, context: ActionContext): Boolean {
    return element is PyStatement
  }

  override fun getPresentation(context: ActionContext, element: PyExpression): Presentation? {
    if (element is PyKeywordArgument || element is PyStarArgument) return null

    val argumentList = element.parent as? PyArgumentList ?: return null
    val arguments = argumentList.arguments
    val caretArgIndex = arguments.indexOf(element)
    if (caretArgIndex < 0) return null

    val convertible = collectConvertibleArguments(argumentList, caretArgIndex, context) ?: return null
    if (convertible.isEmpty()) return null

    return Presentation.of(PyPsiBundle.message("INTN.add.keyword.argument.names"))
  }

  override fun invoke(context: ActionContext, element: PyExpression, updater: ModPsiUpdater) {
    val argumentList = element.parent as? PyArgumentList ?: return
    val arguments = argumentList.arguments
    val caretArgIndex = arguments.indexOf(element)
    if (caretArgIndex < 0) return

    val convertible = collectConvertibleArguments(argumentList, caretArgIndex, context) ?: return

    val generator = PyElementGenerator.getInstance(context.project())
    val languageLevel = LanguageLevel.forElement(element)

    for ((arg, paramName) in convertible) {
      arg.replace(generator.createKeywordArgument(languageLevel, paramName, arg.text))
    }
  }

  /**
   * Resolves parameter names and collects arguments that should be converted to keyword arguments,
   * starting from [caretArgIndex]. Returns a list of (argument, parameterName) pairs,
   * or `null` if conversion is not possible.
   */
  private fun collectConvertibleArguments(
    argumentList: PyArgumentList,
    caretArgIndex: Int,
    context: ActionContext,
  ): List<Pair<PyExpression, String>>? {
    val callExpression = argumentList.parent as? PyCallExpression ?: return null
    val containingFile = argumentList.containingFile ?: return null

    val typeContext = TypeEvalContext.codeAnalysis(context.project(), containingFile)
    val resolveContext = PyResolveContext.defaultContext(typeContext)
    val mapping = callExpression.multiMapArguments(resolveContext).firstOrNull() ?: return null
    val mappedParameters = mapping.mappedParameters

    val arguments = argumentList.arguments

    // Positional unpacking (*args) anywhere in the call breaks positional correspondence
    if (arguments.any { it is PyStarArgument && !it.isKeyword }) return null

    val result = mutableListOf<Pair<PyExpression, String>>()
    for (i in caretArgIndex until arguments.size) {
      val arg = arguments[i]
      if (arg is PyKeywordArgument) continue
      if (arg is PyStarArgument) return null

      val param = mappedParameters[arg] ?: return null
      if (param.isPositionalContainer) return null

      val namedParam = param.parameter as? PyNamedParameter
      if (namedParam != null && namedParam.isPositionalOnly) return null

      val paramName = param.name ?: return null
      result.add(arg to paramName)
    }
    return result
  }
}
