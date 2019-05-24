// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecoratable
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.TYPING_FINAL
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.TYPING_FINAL_EXT
import com.jetbrains.python.psi.types.PyClassType

class PyFinalInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyClass(node: PyClass) {
      super.visitPyClass(node)

      node.superClassExpressions.forEach {
        val cls = (myTypeEvalContext.getType(it) as? PyClassType)?.pyClass
        if (cls != null && isFinal(cls)) {
          registerProblem(it, "'${cls.name}' is marked as '@final' and should not be subclassed")
        }
      }
    }

    private fun isFinal(decoratable: PyDecoratable): Boolean {
      return PyKnownDecoratorUtil.getKnownDecorators(decoratable, myTypeEvalContext).any { it == TYPING_FINAL || it == TYPING_FINAL_EXT }
    }
  }
}