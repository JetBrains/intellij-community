// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.ResolveResult
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.PyWithItem
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Provides a reference for the attribute-name string in `patch.object(Target, "attr")`.
 *
 * Detects:
 * - `@patch.object(Target, "attr")` — second positional argument
 * - `@patch.object(Target, attribute="attr")` — `attribute` keyword argument
 * - `with patch.object(Target, "attr") as mock:` — context manager form
 *
 * When `create=True` is present, the reference is marked soft (no unresolved error).
 */
class PyMockPatchObjectReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val str = element as? PyStringLiteralExpression ?: return emptyArray()
    val callExpr = getPatchObjectCall(str) ?: return emptyArray()

    val createAllowed = isCreateAllowed(callExpr)
    val valueRange = ElementManipulators.getValueTextRange(str)
    val attrName = str.stringValue
    if (attrName.isEmpty()) return emptyArray()

    return arrayOf(PyMockPatchObjectAttrReference(str, valueRange, callExpr, createAllowed))
  }

  private fun isCreateAllowed(callExpr: PyCallExpression): Boolean {
    val createArg = callExpr.getKeywordArgument("create") ?: return false
    return createArg.text == "True"
  }
}

/**
 * Returns the `patch.object(...)` call expression if [str] is the attribute-name argument
 * (second positional or `attribute=` keyword), or null otherwise.
 */
fun getPatchObjectCall(str: PyStringLiteralExpression): PyCallExpression? {
  val argList = when (val parent = str.parent) {
    is PyArgumentList -> parent
    is PyKeywordArgument -> {
      if (parent.keyword != "attribute") return null
      parent.parent as? PyArgumentList ?: return null
    }
    else -> return null
  }

  val callExpr = argList.parent as? PyCallExpression ?: return null

  val patchObjectCall: PyCallExpression = when {
    callExpr is PyDecorator -> callExpr
    callExpr.parent is PyDecorator -> callExpr.parent as PyDecorator
    callExpr.parent is PyWithItem -> callExpr
    else -> return null
  }

  val typeContext = TypeEvalContext.codeAnalysis(str.project, str.containingFile)
  if (!isPatchObjectCall(patchObjectCall, typeContext)) return null

  // Accept as the second positional argument (attribute name)
  val args = argList.arguments
  if (args.size >= 2 && args[1] == str) return patchObjectCall

  // Or as the "attribute" keyword argument
  val attrKeyword = patchObjectCall.getKeywordArgument("attribute")
  if (attrKeyword == str) return patchObjectCall

  return null
}

private class PyMockPatchObjectAttrReference(
  element: PyStringLiteralExpression,
  rangeInElement: TextRange,
  private val callExpr: PyCallExpression,
  private val createAllowed: Boolean,
) : PsiPolyVariantReferenceBase<PyStringLiteralExpression>(element, rangeInElement) {

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val targetClass = resolveTargetClass() ?: return emptyArray()
    val attrName = element.stringValue
    val member = findMember(targetClass, attrName) ?: return emptyArray()
    return arrayOf(PsiElementResolveResult(member))
  }

  override fun isSoft(): Boolean = createAllowed

  override fun getVariants(): Array<Any> {
    val targetClass = resolveTargetClass() ?: return emptyArray()
    return getMemberVariantsOf(targetClass).toTypedArray()
  }

  private fun resolveTargetClass(): PsiElement? {
    val args = callExpr.argumentList?.arguments ?: return null
    val targetExpr = args.firstOrNull() ?: return null
    val context = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
    val type = context.getType(targetExpr)
    if (type is PyClassType) {
      return type.pyClass
    }
    return null
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

  private fun getMemberVariantsOf(target: PsiElement): List<PsiElement> {
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
