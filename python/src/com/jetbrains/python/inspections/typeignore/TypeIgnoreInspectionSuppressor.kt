// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.codeInsight.typing.PyTypingAnnotationInjector
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyPsiUtils

class TypeIgnoreInspectionSuppressor: InspectionSuppressor {

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (element is PsiFile) return false
    if (element.containingFile !is PyFile) return false
    if (toolId !in inspectionsToSuppress) return false
    val comment = PyPsiUtils.findSameLineComment(element) ?: return false
    return PyTypingAnnotationInjector.isTypeIgnoreComment(comment)
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
    return SuppressQuickFix.EMPTY_ARRAY
  }

  companion object {
    private val inspectionsToSuppress = listOf("PyUnresolvedReferences",
                                               "PyTypeHints",
                                               "PyTypeChecker",
                                               "PyRedeclaration",
                                               "PyArgumentList",
                                               "PyFinal",
                                               "PyProtocol",
                                               "PyTypedDict")
  }
}