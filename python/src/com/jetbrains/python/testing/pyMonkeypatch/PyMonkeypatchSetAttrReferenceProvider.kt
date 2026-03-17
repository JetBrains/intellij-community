// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMonkeypatch

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.ResolveResult
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.pyMock.PyMockPatchTargetReferenceSet

/**
 * Provides references for string arguments in `monkeypatch.setattr` and `monkeypatch.delattr`.
 *
 * Two forms are supported:
 *
 * **Dotted-path form** (string target):
 * - `monkeypatch.setattr("module.Class.attr", value)` — first arg is a dotted path
 * - `monkeypatch.delattr("module.Class.attr")` — first arg is a dotted path
 * → Each segment of the dotted path gets a reference (same as `@patch`).
 *
 * **Object + attribute form**:
 * - `monkeypatch.setattr(obj, "attr", value)` — second arg is an attribute name on `obj`
 * - `monkeypatch.delattr(obj, "attr")` — second arg is an attribute name on `obj`
 * → The attribute string gets a single reference resolved against `obj`'s type.
 */
class PyMonkeypatchSetAttrReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val str = element as? PyStringLiteralExpression ?: return emptyArray()
    if (str.stringValue.isEmpty()) return emptyArray()

    val callExpr = findMonkeypatchAttrCall(str) ?: return emptyArray()
    val args = callExpr.argumentList?.arguments ?: return emptyArray()
    val positionalArgs = args.filter { it !is PyKeywordArgument }

    // Form 1: monkeypatch.setattr("dotted.path", value) — string is the first positional arg
    if (positionalArgs.firstOrNull() == str) {
      return PyMockPatchTargetReferenceSet(str, false).createReferences()
    }

    // Form 2: monkeypatch.setattr(obj, "attr", value) — string is the second positional arg
    // Only applies when the first arg is NOT a string (otherwise it's the dotted-path form)
    if (positionalArgs.size >= 2 && positionalArgs[1] == str && positionalArgs[0] !is PyStringLiteralExpression) {
      val valueRange = ElementManipulators.getValueTextRange(str)
      return arrayOf(PyMonkeypatchAttrReference(str, valueRange, callExpr))
    }

    return emptyArray()
  }
}

/**
 * Finds the enclosing `monkeypatch.setattr(...)` or `monkeypatch.delattr(...)` call,
 * or returns null if this string is not inside one.
 */
private fun findMonkeypatchAttrCall(str: PyStringLiteralExpression): PyCallExpression? {
  val argList = str.parent as? com.jetbrains.python.psi.PyArgumentList ?: return null
  val callExpr = argList.parent as? PyCallExpression ?: return null
  val typeContext = TypeEvalContext.codeAnalysis(str.project, str.containingFile)
  if (isMonkeypatchAttrCall(callExpr, "setattr", typeContext)) return callExpr
  if (isMonkeypatchAttrCall(callExpr, "delattr", typeContext)) return callExpr
  return null
}

/**
 * Reference for the attribute name in `monkeypatch.setattr(obj, "attr", value)`.
 * Resolves "attr" against the type of `obj`.
 */
private class PyMonkeypatchAttrReference(
  element: PyStringLiteralExpression,
  rangeInElement: TextRange,
  private val callExpr: PyCallExpression,
) : PsiPolyVariantReferenceBase<PyStringLiteralExpression>(element, rangeInElement) {

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val target = resolveTarget() ?: return emptyArray()
    val attrName = element.stringValue
    val member = findMember(target, attrName) ?: return emptyArray()
    return arrayOf(PsiElementResolveResult(member))
  }

  override fun isSoft(): Boolean = false

  override fun getVariants(): Array<Any> {
    val target = resolveTarget() ?: return emptyArray()
    return getMemberVariants(target).toTypedArray()
  }

  private fun resolveTarget(): PsiElement? {
    val args = callExpr.argumentList?.arguments ?: return null
    val positionalArgs = args.filter { it !is PyKeywordArgument }
    val targetExpr = positionalArgs.firstOrNull() ?: return null
    val context = TypeEvalContext.codeAnalysis(element.project, element.containingFile)

    val type = context.getType(targetExpr)
    if (type is PyClassType) return type.pyClass

    return PyUtil.multiResolveTopPriority(
      targetExpr, PyResolveContext.defaultContext(context),
    ).firstOrNull()
  }

  private fun findMember(target: PsiElement, name: String): PsiElement? {
    val resolved = PyUtil.turnDirIntoInit(target) ?: target
    return when (resolved) {
      is PyClass -> resolved.findMethodByName(name, false, null)
                    ?: resolved.findInstanceAttribute(name, false)
                    ?: resolved.findClassAttribute(name, false, null)
      is PyFile -> resolved.findTopLevelAttribute(name)
                   ?: resolved.findTopLevelFunction(name)
                   ?: resolved.findTopLevelClass(name)
      else -> null
    }
  }

  private fun getMemberVariants(target: PsiElement): List<PsiElement> {
    val resolved = PyUtil.turnDirIntoInit(target) ?: target
    return when (resolved) {
      is PyClass -> resolved.getMethods().toList() +
                    (resolved.classAttributes + resolved.instanceAttributes).mapNotNull { it }
      is PyFile -> resolved.topLevelClasses + resolved.topLevelFunctions +
                   (resolved.topLevelAttributes ?: emptyList<PsiElement>())
      else -> emptyList()
    }
  }
}
