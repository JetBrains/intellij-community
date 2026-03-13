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
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.TypeEvalContext

internal class PyRemoveKeywordArgumentNamesIntention : PsiUpdateModCommandAction<PyKeywordArgument>(PyKeywordArgument::class.java) {

  override fun getFamilyName(): String = PyPsiBundle.message("INTN.NAME.remove.keyword.argument.names")

  override fun isElementApplicable(element: PyKeywordArgument, context: ActionContext): Boolean {
    return element.parent is PyArgumentList
  }

  override fun stopSearchAt(element: PsiElement, context: ActionContext): Boolean {
    return element is PyStatement
  }

  override fun getPresentation(context: ActionContext, element: PyKeywordArgument): Presentation? {
    val convertible = collectConvertibleArguments(element, context) ?: return null
    if (convertible.isEmpty()) return null
    return Presentation.of(PyPsiBundle.message("INTN.remove.keyword.argument.names"))
  }

  override fun invoke(context: ActionContext, element: PyKeywordArgument, updater: ModPsiUpdater) {
    val convertible = collectConvertibleArguments(element, context) ?: return

    val argumentList = element.parent as? PyArgumentList ?: return
    val arguments = argumentList.arguments

    // Build new argument list:
    // 1. Existing positional args and positional unpacking from the start
    // 2. Converted keyword args in parameter order (values only)
    // 3. Remaining unaffected arguments in their original order
    val positionalPrefix = mutableListOf<PyExpression>()
    val remaining = mutableListOf<PyExpression>()

    var seenNonPositional = false
    for (arg in arguments) {
      if (!seenNonPositional && arg !is PyKeywordArgument && !(arg is PyStarArgument && arg.isKeyword)) {
        positionalPrefix.add(arg)
      }
      else {
        seenNonPositional = true
        if (arg !in convertible) {
          remaining.add(arg)
        }
      }
    }

    val generator = PyElementGenerator.getInstance(context.project())
    val languageLevel = LanguageLevel.forElement(element)

    val newArgTexts = mutableListOf<String>()
    for (arg in positionalPrefix) {
      newArgTexts.add(arg.text)
    }
    for (kwArg in convertible) {
      val value = kwArg.valueExpression ?: return
      newArgTexts.add(value.text)
    }
    for (arg in remaining) {
      newArgTexts.add(arg.text)
    }

    val newCallText = "f(${newArgTexts.joinToString(", ")})"
    val newCall = generator.createExpressionFromText(languageLevel, newCallText) as? PyCallExpression ?: return
    val newArgList = newCall.argumentList ?: return
    argumentList.replace(newArgList)
  }

  /**
   * Collects keyword arguments that should be converted to positional, in parameter order.
   * Includes the caret argument and all keyword arguments for preceding parameters.
   * Returns `null` if conversion is not possible.
   */
  private fun collectConvertibleArguments(
    element: PyKeywordArgument,
    context: ActionContext,
  ): Set<PyKeywordArgument>? {
    val argumentList = element.parent as? PyArgumentList ?: return null
    val callExpression = argumentList.parent as? PyCallExpression ?: return null
    val containingFile = argumentList.containingFile ?: return null

    val typeContext = TypeEvalContext.codeAnalysis(context.project(), containingFile)
    val resolveContext = PyResolveContext.defaultContext(typeContext)
    val mapping = callExpression.multiMapArguments(resolveContext).firstOrNull() ?: return null
    if (!mapping.isComplete) return null
    val mappedParameters = mapping.mappedParameters

    val caretParam = mappedParameters[element] ?: return null

    // Reject if keyword-only or keyword container (**kwargs)
    if (caretParam.isKeywordContainer) return null
    // Fail-fast for callable parameters over real PSI parameters.
    // For synthesized callable parameters, e.g. for dataclass constructors, 
    // we will need to look for a preceding keyword-only separator parameter. 
    if ((caretParam.parameter as? PyNamedParameter)?.isKeywordOnly == true) return null

    // Get ordered parameter list, skipping implicit parameters (self, cls)
    val callableType = mapping.callableType ?: return null
    val allParameters = callableType.getParameters(typeContext) ?: return null
    val implicitCount = mapping.implicitParameters.size
    val effectiveParams = allParameters.drop(implicitCount)

    val caretParamIndex = effectiveParams.indexOf(caretParam)
    if (caretParamIndex < 0) return null

    val arguments = argumentList.arguments

    // Positional unpacking (*args) in the call breaks positional correspondence
    if (arguments.any { it is PyStarArgument && !it.isKeyword }) return null

    // Build a map from PSI parameter to the call-site argument expression
    val paramToArg = mutableMapOf<PyCallableParameter, PyExpression>()
    for ((arg, callableParam) in mappedParameters) {
      paramToArg[callableParam] = arg
    }

    // Check all params from 0 to caretParamIndex have explicit arguments;
    // collect keyword args that need converting
    val result = mutableSetOf<PyKeywordArgument>()
    for (param in effectiveParams.take(caretParamIndex + 1)) {

      // Skip positional-only separator (/)
      if (param.isPositionOnlySeparator) continue

      // A positional/keyword vararg or keyword-only separator (*) before the caret
      // means subsequent params are keyword-only. 
      // Checking it again is necessary for synthesized callable parameters.
      if (param.isPositionalContainer || param.isKeywordContainer || param.isKeywordOnlySeparator) return null

      val psiParam = param ?: return null
      val arg = paramToArg[psiParam]

      if (arg == null) {
        // No explicit argument for this parameter — can't convert to positional.
        // This also handles the case where **kwargs unpacking in the call could
        // satisfy this parameter, since such mappings are not in mappedParameters.
        return null
      }

      if (arg is PyKeywordArgument) {
        result.add(arg)
      }
    }

    return result
  }
}
