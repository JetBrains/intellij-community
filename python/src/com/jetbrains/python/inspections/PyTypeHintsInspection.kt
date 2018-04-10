// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.controlflow.ControlFlowUtil
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveUtil

class PyTypeHintsInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyCallExpression(node: PyCallExpression?) {
      super.visitPyCallExpression(node)

      if (node != null) {
        val callee = node.callee as? PyReferenceExpression
        if (callee != null &&
            QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_VAR) in PyResolveUtil.resolveImportedElementQNameLocally(callee)) {
          val target = (node.parent as? PyAssignmentStatement)?.targetsToValuesMapping?.firstOrNull { it.second == node }?.first

          checkTypeVarPlacement(node, target)
          checkTypeVarRedefinition(target)

          if (target != null) {
            checkTypeVarName(node, target)
          }
        }
      }
    }

    private fun checkTypeVarPlacement(call: PyCallExpression, target: PyExpression?) {
      if (target == null) {
        registerProblem(call, "A 'TypeVar()' expression must always directly be assigned to a variable")
      }
    }

    private fun checkTypeVarName(call: PyCallExpression, target: PyExpression) {
      val name = call.getArgument(0, "name", PyStringLiteralExpression::class.java)

      if (name != null && name.stringValue != target.name) {
        registerProblem(name, "The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned")
      }
    }

    private fun checkTypeVarRedefinition(target: PyExpression?) {
      val scopeOwner = ScopeUtil.getScopeOwner(target) ?: return
      val name = target?.name ?: return

      val instructions = ControlFlowCache.getControlFlow(scopeOwner).instructions
      val startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, target)

      ControlFlowUtil.iteratePrev(
        startInstruction,
        instructions,
        { instruction ->
          if (instruction is ReadWriteInstruction &&
              instruction.num() != startInstruction &&
              name == instruction.name &&
              instruction.access.isWriteAccess) {
            registerProblem(target, "Type variables must not be redefined")
            ControlFlowUtil.Operation.BREAK
          }
          else {
            ControlFlowUtil.Operation.NEXT
          }
        }
      )
    }
  }
}