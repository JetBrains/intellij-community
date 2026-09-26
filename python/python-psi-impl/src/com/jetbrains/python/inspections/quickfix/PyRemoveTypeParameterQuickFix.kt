package com.jetbrains.python.inspections.quickfix

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.PyTypeParameterList

/**
 * Removes a single type parameter from a type parameter list.
 * If the list becomes empty, removes the brackets as well.
 */
class PyRemoveTypeParameterQuickFix : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.NAME.remove.type.parameter")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val typeParameter = PsiTreeUtil.getParentOfType(element, PyTypeParameter::class.java, false) ?: return
    val list = PsiTreeUtil.getParentOfType(typeParameter, PyTypeParameterList::class.java, false) ?: return

    var prev: PsiElement? = typeParameter.prevSibling
    var next: PsiElement? = typeParameter.nextSibling

    if (prev is PsiWhiteSpace) prev = prev.prevSibling
    if (next is PsiWhiteSpace) next = next.nextSibling

    if (prev != null && prev.node.elementType == PyTokenTypes.COMMA) {
      prev.delete()
      val afterComma = typeParameter.prevSibling
      if (afterComma is PsiWhiteSpace) afterComma.delete()
    }
    else if (next != null && next.node.elementType == PyTokenTypes.COMMA) {
      val ws = next.nextSibling
      next.delete()
      if (ws is PsiWhiteSpace) ws.delete()
    }

    typeParameter.delete()

    if (list.typeParameters.isEmpty()) {
      (list as PyElement).delete()
    }
  }
}
