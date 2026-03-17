// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyWithItem
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Provides references for the target string in `@patch("module.Class.attr")` and related
 * decorator calls and context managers.
 *
 * Supported calls:
 * - `patch("target")` / `patch(target="target")` — dotted path to attribute being patched
 * - `patch.dict("target", ...)` / `patch.dict(in_dict="target", ...)` — dotted path to dict
 * - `patch.multiple("target", ...)` / `patch.multiple(target="target", ...)` — dotted path to module/class
 *
 * When `create=True` is present, references are marked soft (no unresolved error).
 */
class PyMockPatchReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val str = element as? PyStringLiteralExpression ?: return emptyArray()
    val callExpr = getPatchTargetCall(str) ?: return emptyArray()

    val createAllowed = isCreateAllowed(callExpr)
    return PyMockPatchTargetReferenceSet(str, createAllowed).createReferences()
  }

  private fun isCreateAllowed(callExpr: PyCallExpression): Boolean {
    val createArg = callExpr.getKeywordArgument("create") ?: return false
    return createArg.text == "True"
  }
}

/**
 * Returns the `patch(...)`, `patch.dict(...)`, or `patch.multiple(...)` call expression
 * if [str] is the string target argument, or null otherwise.
 *
 * For `patch()` and `patch.multiple()`, the keyword form is `target=`.
 * For `patch.dict()`, the keyword form is `in_dict=`.
 */
fun getPatchCall(str: PyStringLiteralExpression): PyCallExpression? =
  getPatchTargetCall(str)

private fun getPatchTargetCall(str: PyStringLiteralExpression): PyCallExpression? {
  val keyword = (str.parent as? PyKeywordArgument)
  val argList = when (val parent = str.parent) {
    is PyArgumentList -> parent
    is PyKeywordArgument -> parent.parent as? PyArgumentList ?: return null
    else -> return null
  }

  val callExpr = argList.parent as? PyCallExpression ?: return null

  val effectiveCall: PyCallExpression = when {
    callExpr is PyDecorator -> callExpr
    callExpr.parent is PyDecorator -> callExpr.parent as PyDecorator
    callExpr.parent is PyWithItem -> callExpr
    else -> return null
  }

  val typeContext = TypeEvalContext.codeAnalysis(str.project, str.containingFile)

  // Check which patch variant this is and validate the argument position
  return when {
    isPatchCall(effectiveCall, typeContext) ->
      validateTargetArg(str, keyword, argList, effectiveCall, "target")

    isPatchDictCall(effectiveCall, typeContext) ->
      validateTargetArg(str, keyword, argList, effectiveCall, "in_dict")

    isPatchMultipleCall(effectiveCall, typeContext) ->
      validateTargetArg(str, keyword, argList, effectiveCall, "target")

    else -> null
  }
}

/**
 * Returns [effectiveCall] if [str] is the first positional argument or the named [keywordName]
 * argument, null otherwise.
 */
private fun validateTargetArg(
  str: PyStringLiteralExpression,
  keyword: PyKeywordArgument?,
  argList: PyArgumentList,
  effectiveCall: PyCallExpression,
  keywordName: String,
): PyCallExpression? {
  // First positional argument
  if (keyword == null && argList.arguments.firstOrNull() == str) return effectiveCall

  // Named keyword argument
  if (keyword != null && keyword.keyword == keywordName) return effectiveCall

  return null
}
