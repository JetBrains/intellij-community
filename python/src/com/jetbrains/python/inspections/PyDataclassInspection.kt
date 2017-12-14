/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.codeInsight.stdlib.parseDataclassParameters
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyClassType

class PyDataclassInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyTargetExpression(node: PyTargetExpression?) {
      super.visitPyTargetExpression(node)

      val cls = getPyClass(node?.qualifier) ?: return
      if (parseDataclassParameters(cls, myTypeEvalContext)?.frozen == true) {
        registerProblem(node, "'${cls.name}' object attribute '${node!!.name}' is read-only", ProblemHighlightType.GENERIC_ERROR)
      }
    }

    override fun visitPyClass(node: PyClass?) {
      super.visitPyClass(node)

      if (node != null) {
        val dataclassParameters = parseDataclassParameters(node, myTypeEvalContext)

        if (dataclassParameters != null) {
          if (!dataclassParameters.eq && dataclassParameters.order) {
            val eqArgument = dataclassParameters.eqArgument
            if (eqArgument != null) {
              registerProblem(eqArgument, "eq must be true if order is true", ProblemHighlightType.GENERIC_ERROR)
            }
          }

          node.processClassLevelDeclarations { element, _ ->
            if (element is PyTargetExpression && element.annotationValue != null) {
              val value = element.findAssignedValue()
              val cls = getPyClass(value)

              if (cls != null) {
                val builtinCache = PyBuiltinCache.getInstance(node)

                if (cls == builtinCache.listType?.pyClass ||
                    cls == builtinCache.setType?.pyClass ||
                    cls == builtinCache.tupleType?.pyClass) {
                  registerProblem(value, "mutable default '${cls.name}' is not allowed", ProblemHighlightType.GENERIC_ERROR)
                }
              }
            }

            true
          }

          PyNamedTupleInspection.inspectFieldsOrder(node, this::registerProblem)
        }
      }
    }

    private fun getPyClass(element: PyTypedElement?): PyClass? {
      return (element?.let { myTypeEvalContext.getType(it) } as? PyClassType)?.pyClass
    }
  }
}