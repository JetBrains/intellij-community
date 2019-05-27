// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.TYPING_FINAL
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.TYPING_FINAL_EXT
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.pyi.PyiUtil

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

      if (PyiUtil.isInsideStub(node)) {
        val visitedNames = mutableSetOf<String?>()

        node.visitMethods(
          { m ->
            if (!visitedNames.add(m.name) && isFinal(m)) {
              registerProblem(m.nameIdentifier, "'@final' should be placed on the first overload")
            }
            true
          },
          false,
          myTypeEvalContext
        )
      }
    }

    override fun visitPyFunction(node: PyFunction) {
      super.visitPyFunction(node)

      if (node.containingClass != null) {
        PySuperMethodsSearch
          .search(node, myTypeEvalContext)
          .firstOrNull { it is PyFunction && isFinal(it) }
          ?.let {
            registerProblem(node.nameIdentifier, "'${(it as PyFunction).qualifiedName}' is marked as '@final' and should not be overridden")
          }

        if (!PyiUtil.isInsideStub(node) && isFinal(node) && PyiUtil.isOverload(node, myTypeEvalContext)) {
          registerProblem(node.nameIdentifier, "'@final' should be placed on the implementation")
        }
      }
      else if (isFinal(node)) {
        registerProblem(node.nameIdentifier, "Non-method function could not be marked as '@final'")
      }
    }

    override fun visitPyTargetExpression(node: PyTargetExpression) {
      super.visitPyTargetExpression(node)

      if (!node.hasAssignedValue()) {
        val value = node.annotation?.value
        if (value is PyReferenceExpression) {
          value
            .multiFollowAssignmentsChain(resolveContext) { !isFinal(it.qualifiedName) }
            .asSequence()
            .mapNotNull { it.element }
            .any { it is PyTargetExpression && isFinal(it.qualifiedName) }
            .let {
              if (it) registerProblem(value, "If assigned value is omitted, there should be an explicit type argument to 'Final'")
            }
        }
      }
    }

    private fun isFinal(decoratable: PyDecoratable): Boolean {
      return PyKnownDecoratorUtil.getKnownDecorators(decoratable, myTypeEvalContext).any { it == TYPING_FINAL || it == TYPING_FINAL_EXT }
    }

    private fun isFinal(qualifiedName: String?): Boolean {
      return qualifiedName == PyTypingTypeProvider.FINAL || qualifiedName == PyTypingTypeProvider.FINAL_EXT
    }
  }
}