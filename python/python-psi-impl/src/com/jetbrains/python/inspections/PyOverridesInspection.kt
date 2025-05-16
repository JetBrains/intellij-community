// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext

class PyOverridesInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor =
    Visitor(holder, PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyFunction(node: PyFunction) {
      super.visitPyFunction(node)

      val overrideDecorator = node.decoratorList?.decorators?.firstOrNull { decorator ->
        PyKnownDecoratorUtil.asKnownDecorators(decorator, myTypeEvalContext).any {
          it == PyKnownDecorator.TYPING_OVERRIDE || it == PyKnownDecorator.TYPING_EXTENSIONS_OVERRIDE
        }
      } ?: return

      val superMethods = PySuperMethodsSearch.search(node, myTypeEvalContext).findAll()
      if (superMethods.isEmpty()) {
        registerProblem(overrideDecorator, PyPsiBundle.message("INSP.override.missing.super.method"))
      }
    }
  }
}
