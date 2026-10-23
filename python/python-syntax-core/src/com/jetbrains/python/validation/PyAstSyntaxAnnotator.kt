// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation

import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement

/**
 * Runs the syntax checks that need only the AST. It is `DumbAware`, so the light mode of JetBrains Light and the JetBrains
 * Client frontend report them without the backend modules. A check that needs resolve, a quick fix from
 * `intellij.python.psi.impl`, or the console context stays in `PySyntaxAnnotator` there.
 */
class PyAstSyntaxAnnotator : PyAnnotatorBase(), DumbAware {
  override fun annotate(element: PsiElement, holder: PyAnnotationHolder) {
    val visitors = listOf(
      PyTypeAnnotationTargetAnnotatorVisitor(holder),
      PyParameterListAnnotatorVisitor(holder),
      PyReturnYieldAnnotatorVisitor(holder),
      PyBreakContinueAnnotatorVisitor(holder),
      PyGlobalAnnotatorVisitor(holder),
      PyImportAnnotatorVisitor(holder),
      PyAstNumericLiteralAnnotatorVisitor(holder),
      PyGeneratorInArgumentListAnnotatorVisitor(holder),
      PyStringLiteralQuotesAnnotatorVisitor(holder),
      PyFStringsAnnotatorVisitor(holder),
      PyTypeParameterListAnnotatorVisitor(holder),
    )
    for (visitor in visitors) {
      element.accept(visitor)
    }
  }
}
