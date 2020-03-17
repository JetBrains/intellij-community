package com.jetbrains.python.psi.impl.references.hasattr

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PlatformIcons
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.ResolveResultList
import com.jetbrains.python.psi.resolve.RatedResolveResult

object PyHasAttrHelper {
  fun addHasAttrResolveResults(psiElement: PsiElement, referencedName: String, qualifier: PyExpression, ret: ResolveResultList) {
    val hasAttrVariants = getHasAttrVariantsFromContext(psiElement, qualifier)
    for (variant in hasAttrVariants.keys) {
      if (variant == referencedName) {
        ret.add(RatedResolveResult(RatedResolveResult.RATE_NORMAL, hasAttrVariants[variant]))
        return
      }
    }
  }

  fun addHasAttrCompletionResults(psiElement: PsiElement, qualifier: PyExpression,
                                  namesAlready: MutableSet<String>, variants: MutableCollection<Any>) {
    for (variant in variants) {
      if (variant is LookupElement) {
        namesAlready.add(variant.lookupString)
      }
      else {
        namesAlready.add(variant.toString())
      }
    }

    for (variant in getHasAttrVariantsFromContext(psiElement, qualifier).keys) {
      if (!namesAlready.contains(variant)) {
        variants.add(LookupElementBuilder.create(variant)
                       .withTypeText(PyNames.HAS_ATTR)
                       .withIcon(PlatformIcons.FIELD_ICON))
        namesAlready.add(variant)
      }
    }
  }

  private fun getHasAttrVariantsFromContext(psiElement: PsiElement, qualifier: PyExpression): Map<String, PsiElement> {
    val result = hashMapOf<String, PsiElement>()
    result.putAll(getHasAttrVariantsFromAnd(psiElement, qualifier))
    result.putAll(getHasAttrVariantsFromConditions(psiElement, qualifier))
    return result
  }

  private fun getHasAttrVariantsFromAnd(psiElement: PsiElement, qualifier: PyExpression): Map<String, PsiElement> {
    val result = hashMapOf<String, PsiElement>()
    val binaryExpr = PsiTreeUtil.getParentOfType(psiElement, PyBinaryExpression::class.java) ?: return result
    if (!binaryExpr.isOperator(PyNames.AND)) return result
    if (!PsiTreeUtil.isAncestor(binaryExpr.rightExpression, psiElement, false)) return result
    result.putAll(getHasAttrVisitorResultOn(binaryExpr.leftExpression, qualifier))
    return result
  }

  private fun getHasAttrVariantsFromConditions(psiElement: PsiElement, qualifier: PyExpression): Map<String, PsiElement> {
    val result = hashMapOf<String, PsiElement>()

    var curParent = PsiTreeUtil.getParentOfType(psiElement, PyIfPart::class.java, PyConditionalExpression::class.java)
    while (curParent != null) {
      val condition = when {
        curParent is PyIfPart && PsiTreeUtil.isAncestor(curParent.statementList, psiElement, true) -> curParent.condition
        curParent is PyConditionalExpression && PsiTreeUtil.isAncestor(curParent.truePart, psiElement, false) -> curParent.condition
        else -> null
      }
      if (condition != null) {
        result.putAll(getHasAttrVisitorResultOn(condition, qualifier))
      }
      curParent = PsiTreeUtil.getParentOfType(curParent, PyIfPart::class.java, PyConditionalExpression::class.java)
    }

    return result
  }

  private fun getHasAttrVisitorResultOn(psiElement: PsiElement, qualifier: PyExpression): Map<String, PsiElement> {
    if (qualifier !is PyReferenceExpression) return hashMapOf()
    val resolvedQualifier = qualifier.reference.resolve() ?: return hashMapOf()
    val pyHasAttrVisitor = PyHasAttrVisitor(resolvedQualifier)
    psiElement.accept(pyHasAttrVisitor)
    return pyHasAttrVisitor.result
  }
}