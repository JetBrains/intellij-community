/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.codeInsight.stdlib.parseDataclassParameters
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.*

class PyDataclassInspection : PyInspection() {

  companion object {
    private val ORDER_OPERATORS = setOf("__lt__", "__le__", "__gt__", "__ge__")
    private val DATACLASSES_HELPERS = setOf("dataclasses.fields", "dataclasses.asdict", "dataclasses.astuple", "dataclasses.replace")
  }

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyTargetExpression(node: PyTargetExpression?) {
      super.visitPyTargetExpression(node)

      val cls = getInstancePyClass(node?.qualifier) ?: return
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
              val annotation = element.annotation

              if (annotation != null && !PyTypingTypeProvider.isClassVarAnnotation(annotation, myTypeEvalContext)) {
                val value = element.findAssignedValue()
                val cls = getInstancePyClass(value)

                if (cls != null) {
                  val builtinCache = PyBuiltinCache.getInstance(node)

                  if (cls == builtinCache.listType?.pyClass ||
                      cls == builtinCache.setType?.pyClass ||
                      cls == builtinCache.tupleType?.pyClass) {
                    registerProblem(value,
                                    "mutable default '${cls.name}' is not allowed",
                                    ProblemHighlightType.GENERIC_ERROR)
                  }
                }
              }
            }

            true
          }

          PyNamedTupleInspection.inspectFieldsOrder(node, myTypeEvalContext, this::registerProblem)
        }
      }
    }

    override fun visitPyBinaryExpression(node: PyBinaryExpression?) {
      super.visitPyBinaryExpression(node)

      if (node != null && ORDER_OPERATORS.contains(node.referencedName)) {
        val leftClass = getInstancePyClass(node.leftExpression) ?: return
        val rightClass = getInstancePyClass(node.rightExpression) ?: return

        val leftDataclassParameters = parseDataclassParameters(leftClass, myTypeEvalContext)

        if (leftClass != rightClass &&
            leftDataclassParameters != null &&
            parseDataclassParameters(rightClass, myTypeEvalContext) != null) {
          registerProblem(node.psiOperator,
                          "${node.referencedName} not supported between instances of '${leftClass.name}' and '${rightClass.name}'",
                          ProblemHighlightType.GENERIC_ERROR)
        }

        if (leftClass == rightClass && leftDataclassParameters?.order == false) {
          registerProblem(node.psiOperator,
                          "${node.referencedName} not supported between instances of '${leftClass.name}'",
                          ProblemHighlightType.GENERIC_ERROR)
        }
      }
    }

    override fun visitPyCallExpression(node: PyCallExpression?) {
      super.visitPyCallExpression(node)

      if (node != null) {
        val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext)
        val markedCallee = node.multiResolveCallee(resolveContext).singleOrNull()
        val callee = markedCallee?.element
        val calleeQName = callee?.qualifiedName

        if (markedCallee != null && callee != null && DATACLASSES_HELPERS.contains(calleeQName)) {
          val mapping = PyCallExpressionHelper.mapArguments(node, markedCallee, myTypeEvalContext)

          val dataclassParameter = callee.getParameters(myTypeEvalContext).firstOrNull()
          val dataclassArgument = mapping.mappedParameters.entries.firstOrNull { it.value == dataclassParameter }?.key

          processHelperDataclassArgument(dataclassArgument, calleeQName!!)
        }
      }
    }

    private fun getInstancePyClass(element: PyTypedElement?): PyClass? {
      val type = element?.let { myTypeEvalContext.getType(it) } as? PyClassType
      return if (type != null && !type.isDefinition) type.pyClass else null
    }

    private fun processHelperDataclassArgument(argument: PyExpression?, calleeQName: String) {
      if (argument == null) return

      val allowDefinition = calleeQName == "dataclasses.fields"

      if (isNotDataclass(myTypeEvalContext.getType(argument), allowDefinition)) {
        val message = "'$calleeQName' method should be called on dataclass instances" + if (allowDefinition) " or types" else ""

        registerProblem(argument, message)
      }
    }

    private fun isNotDataclass(type: PyType?, allowDefinition: Boolean): Boolean {
      if (type is PyStructuralType || PyTypeChecker.isUnknown(type, myTypeEvalContext)) return false
      if (type is PyUnionType) return type.members.all { isNotDataclass(it, allowDefinition) }

      return type !is PyClassType ||
             !allowDefinition && type.isDefinition ||
             parseDataclassParameters(type.pyClass, myTypeEvalContext) == null
    }
  }
}