// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecoratable
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyDisjointBaseUtil
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyDisjointBaseInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val context = PyInspectionVisitor.getContext(session)
    if (context.usesExternalTypeEngine) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return Visitor(holder, context)
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyClass(node: PyClass) {
      super.visitPyClass(node)

      val superClassTypes = node.getSuperClassTypes(myTypeEvalContext).filterIsInstance<PyClassType>()
      if (superClassTypes.size < 2) return

      for (i in superClassTypes.indices) {
        val type1 = superClassTypes[i]
        val name1 = type1.name ?: continue

        for (j in i + 1 until superClassTypes.size) {
          val type2 = superClassTypes[j]
          val name2 = type2.name ?: continue

          if (PyDisjointBaseUtil.areDisjoint(type1, type2, myTypeEvalContext)) {
            registerProblem(
              node.nameIdentifier ?: node,
              PyPsiBundle.message("INSP.disjoint.base.class.cannot.inherit.from.disjoint.bases", name1, name2)
            )
            return
          }
        }
      }
    }

    override fun visitPyDecorator(node: PyDecorator) {
      val decorator = PyKnownDecoratorUtil.asKnownDecorators(node, myTypeEvalContext)
      if (decorator.contains(PyKnownDecorator.DISJOINT_BASE) || decorator.contains(PyKnownDecorator.DISJOINT_BASE_EXT)) {
        val parent = node.parentOfType<PyDecoratable>()
        if (parent is PyFunction) {
          registerProblem(node, PyPsiBundle.message("INSP.disjoint.base.on.function"))
          return
        }
        if (parent is PyClass) {
          if (parent.isProtocol(myTypeEvalContext)) {
            registerProblem(node, PyPsiBundle.message("INSP.disjoint.base.on.protocol"))
            return
          }

          if (parent.isTypingTypedDictInheritor(myTypeEvalContext)) {
            registerProblem(node, PyPsiBundle.message("INSP.disjoint.base.on.typed.dict"))
            return
          }
        }
      }
      super.visitPyDecorator(node)
    }
  }
}
