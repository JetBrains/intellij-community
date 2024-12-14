// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyPsiUtils

class TypeIgnoreInspectionSuppressor : InspectionSuppressor {

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (element is PsiFile) return false
    val containingFile = element.containingFile
    if (containingFile !is PyFile) return false
    if (toolId !in inspectionsToSuppress) return false
    if (isSuppressedForFile(containingFile)) return true
    val comment = PyPsiUtils.findSameLineComment(element) ?: return false
    return isTypeIgnore(comment)
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
    return SuppressQuickFix.EMPTY_ARRAY
  }
}

private fun isSuppressedForFile(file: PsiFile): Boolean {
  var node = file.firstChild
  while (node is PsiComment || node is PsiWhiteSpace) {
    if (node is PsiComment && isTypeIgnore(node)) {
      return true
    }
    node = node.getNextSibling()
  }
  return false
}

private fun isTypeIgnore(comment: PsiComment): Boolean {
  val text = comment.text ?: return false
  return PyTypingTypeProvider.TYPE_IGNORE_PATTERN.matcher(text).matches()
}

private val inspectionsToSuppress = listOf("PyUnresolvedReferences",
                                           "PyTypeHints",
                                           "PyTypeChecker",
                                           "PyRedeclaration",
                                           "PyArgumentList",
                                           "PyFinal",
                                           "PyProtocol",
                                           "PyTypedDict")
