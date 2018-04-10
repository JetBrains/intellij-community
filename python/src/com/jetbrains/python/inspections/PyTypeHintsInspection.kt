// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.controlflow.ControlFlowUtil
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.PyTypeChecker

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
          checkTypeVarArguments(node, target)
          checkTypeVarRedefinition(target)
        }
      }
    }

    private fun checkTypeVarPlacement(call: PyCallExpression, target: PyExpression?) {
      if (target == null) {
        registerProblem(call, "A 'TypeVar()' expression must always directly be assigned to a variable")
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

    private fun checkTypeVarArguments(call: PyCallExpression, target: PyExpression?) {
      val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext)
      var covariant = false
      var contravariant = false
      var bound: PyExpression? = null
      val constraints = mutableListOf<PyExpression?>()

      call.multiMapArguments(resolveContext).firstOrNull { it.unmappedArguments.isEmpty() && it.unmappedParameters.isEmpty() }?.let {
        it.mappedParameters.entries.forEach {
          val name = it.value.name
          val argument = PyUtil.peelArgument(it.key)

          when (name) {
            "name" ->
              if (argument !is PyStringLiteralExpression) {
                registerProblem(argument, "'TypeVar()' expects a string literal as first argument")
              }
              else if (target != null && argument.stringValue != target.name) {
                registerProblem(argument, "The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned")
              }
            "covariant" -> covariant = PyEvaluator.evaluateAsBoolean(argument, false)
            "contravariant" -> contravariant = PyEvaluator.evaluateAsBoolean(argument, false)
            "bound" -> bound = argument
            "constraints" -> constraints.add(argument)
          }
        }
      }

      if (covariant && contravariant) {
        registerProblem(call, "Bivariant type variables are not supported", ProblemHighlightType.GENERIC_ERROR)
      }

      if (constraints.isNotEmpty() && bound != null) {
        registerProblem(call, "Constraints cannot be combined with bound=...", ProblemHighlightType.GENERIC_ERROR)
      }

      if (constraints.size == 1) {
        registerProblem(call, "A single constraint is not allowed", ProblemHighlightType.GENERIC_ERROR)
      }

      constraints.asSequence().plus(bound).forEach {
        if (it != null) {
          val type = PyTypingTypeProvider.getType(it, myTypeEvalContext)?.get()

          if (PyTypeChecker.hasGenerics(type, myTypeEvalContext)) {
            registerProblem(it, "Constraints cannot be parametrized by type variables")
          }
        }
      }
    }
  }
}