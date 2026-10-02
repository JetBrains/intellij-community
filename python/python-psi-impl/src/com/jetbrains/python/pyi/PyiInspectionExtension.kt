// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pyi

import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyiInspectionExtension : PyInspectionExtension() {
  override fun ignoreUnused(element: PsiElement, evalContext: TypeEvalContext): Boolean {
    if (element.containingFile !is PyiFile) {
      return false
    }
    val elements = when (element) {
      is PyFromImportStatement -> if (element.isStarImport) emptyList() else element.importElements.toList()
      is PyImportStatement -> element.importElements.toList()
      is PyImportElement -> listOf(element)
      else -> emptyList()
    }
    return elements.isEmpty() || elements.any { it.asName != null }
  }

  override fun ignoreProtectedSymbol(expression: PyReferenceExpression, context: TypeEvalContext): Boolean {
    val referencedElement = expression.reference.resolve()
    return referencedElement?.containingFile is PyiFile
  }
}
