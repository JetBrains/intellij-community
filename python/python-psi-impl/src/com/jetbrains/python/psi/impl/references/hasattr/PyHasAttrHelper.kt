package com.jetbrains.python.psi.impl.references.hasattr

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*

object PyHasAttrHelper {
  fun getNamesFromHasAttrs(psiElement: PsiElement, qualifier: PyExpression): Set<String> =
    getHasAttrVariantsFromContext(psiElement, qualifier)

  private fun getHasAttrVariantsFromContext(psiElement: PsiElement, qualifier: PyExpression): Set<String> {
    val result = hashSetOf<String>()
    result.addAll(getHasAttrVariantsFromAnd(psiElement, qualifier))
    result.addAll(getHasAttrVariantsFromConditions(psiElement, qualifier))
    return result
  }

  private fun getHasAttrVariantsFromAnd(psiElement: PsiElement, qualifier: PyExpression): Set<String> {
    val result = hashSetOf<String>()
    val binaryExpr = PsiTreeUtil.getParentOfType(psiElement, PyBinaryExpression::class.java) ?: return result
    if (!binaryExpr.isOperator(PyNames.AND)) return result
    if (!PsiTreeUtil.isAncestor(binaryExpr.rightExpression, psiElement, false)) return result
    result.addAll(getHasAttrVisitorResultOn(binaryExpr.leftExpression, qualifier))
    return result
  }

  private fun getHasAttrVariantsFromConditions(psiElement: PsiElement, qualifier: PyExpression): Set<String> {
    val result = hashSetOf<String>()

    var curParent = PsiTreeUtil.getParentOfType(psiElement, PyIfPart::class.java, PyConditionalExpression::class.java)
    while (curParent != null) {
      val condition = when {
        curParent is PyIfPart && PsiTreeUtil.isAncestor(curParent.statementList, psiElement, true) -> curParent.condition
        curParent is PyConditionalExpression && PsiTreeUtil.isAncestor(curParent.truePart, psiElement, false) -> curParent.condition
        else -> null
      }
      if (condition != null) {
        result.addAll(getHasAttrVisitorResultOn(condition, qualifier))
      }
      curParent = PsiTreeUtil.getParentOfType(curParent, PyIfPart::class.java, PyConditionalExpression::class.java)
    }

    return result
  }

  private fun getHasAttrVisitorResultOn(psiElement: PsiElement, qualifier: PyExpression): Set<String> {
    if (qualifier !is PyReferenceExpression) return emptySet()
    val resolvedQualifier = qualifier.reference.resolve() ?: return emptySet()
    val pyHasAttrVisitor = PyHasAttrVisitor(resolvedQualifier)
    psiElement.accept(pyHasAttrVisitor)
    return pyHasAttrVisitor.result
  }
}