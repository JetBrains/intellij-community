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
 * Provides references for the target string in `@patch("module.Class.attr")` decorator calls
 * and `with patch("module.Class.attr") as mock:` context managers.
 *
 * Detects:
 * - `@patch("target")` / `with patch("target")` — first positional argument
 * - `@patch(target="target")` / `with patch(target="target")` — keyword argument named `target`
 *
 * When `create=True` is present, references are marked soft (no unresolved error).
 */
class PyMockPatchReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val str = element as? PyStringLiteralExpression ?: return emptyArray()
    val callExpr = getPatchCall(str) ?: return emptyArray()

    val createAllowed = isCreateAllowed(callExpr)
    return PyMockPatchTargetReferenceSet(str, createAllowed).createReferences()
  }

  private fun isCreateAllowed(callExpr: PyCallExpression): Boolean {
    val createArg = callExpr.getKeywordArgument("create") ?: return false
    return createArg.text == "True"
  }
}

/**
 * Returns the `patch(...)` call expression if [str] is the patch target argument
 * (either the first positional arg or the `target=` keyword arg), or null otherwise.
 *
 * Supports two usage styles:
 * - `@patch("target")` / `@patch(target="target")` — decorator
 * - `with patch("target") as mock:` / `with patch(target="target") as mock:` — context manager
 *
 * PSI parent chain for positional arg:
 *   `PyStringLiteralExpression → PyArgumentList → PyCallExpression → PyDecorator/PyWithItem`
 *
 * PSI parent chain for `target=` kwarg:
 *   `PyStringLiteralExpression → PyKeywordArgument → PyArgumentList → PyCallExpression → PyDecorator/PyWithItem`
 *
 * Note: `PyDecorator.getArgumentList()` delegates to a child `PyCallExpression`, so the
 * `PyArgumentList` is a grandchild of `PyDecorator`, not a direct child.
 */
fun getPatchCall(str: PyStringLiteralExpression): PyCallExpression? {
  val argList = when (val parent = str.parent) {
    is PyArgumentList -> parent
    is PyKeywordArgument -> parent.parent as? PyArgumentList ?: return null
    else -> return null
  }

  val callExpr = argList.parent as? PyCallExpression ?: return null

  // Determine the effective patch call and verify it is in a recognised context.
  // For @patch decorators, getArgumentList() delegates to a child PyCallExpression,
  // so callExpr may be the inner call — the PyDecorator is then its parent.
  val patchCall: PyCallExpression = when {
    callExpr is PyDecorator -> callExpr
    callExpr.parent is PyDecorator -> callExpr.parent as PyDecorator
    callExpr.parent is PyWithItem -> callExpr
    else -> return null
  }

  val typeContext = TypeEvalContext.codeAnalysis(str.project, str.containingFile)
  if (!isPatchCall(patchCall, typeContext)) return null

  // Accept only the first positional arg or the "target" keyword arg
  if (argList.arguments.firstOrNull() == str) return patchCall

  val targetKeyword = patchCall.getKeywordArgument("target")
  if (targetKeyword == str) return patchCall

  return null
}
