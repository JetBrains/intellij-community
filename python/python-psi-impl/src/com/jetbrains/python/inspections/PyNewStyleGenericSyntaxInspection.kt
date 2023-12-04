package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyNewStyleGenericSyntaxInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder,
                                                                                              PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyTypeParameter(typeParameter: PyTypeParameter) {
      val boundExpression = typeParameter.boundExpression
      if (boundExpression != null) {
        val boundElementsToProcess =
          PsiTreeUtil.findChildrenOfAnyType(boundExpression, false, PyReferenceExpression::class.java, PyLiteralExpression::class.java)

        boundElementsToProcess
          .filterIsInstance<PyReferenceExpression>()
          .associateWith { Ref.deref(PyTypingTypeProvider.getType(it, myTypeEvalContext)) }
          .filter { (_, v) -> v is PyTypeVarType }
          .forEach { (k, _) ->
            registerProblem(k,
                            PyPsiBundle.message("INSP.new.style.generics.are.not.allowed.inside.type.param.bounds"),
                            ProblemHighlightType.GENERIC_ERROR)
          }
      }
    }

    override fun visitPyTypeAliasStatement(node: PyTypeAliasStatement) {
      val typeExpression = node.typeExpression
      if (typeExpression != null) {
        reportOldStyleTypeVarsUsage(typeExpression,
                                    PyPsiBundle.message(
                                      "INSP.new.style.generics.old.style.type.vars.not.allowed.in.new.style.type.aliases"))
        reportAssignmentExpressions(typeExpression,
                                    PyPsiBundle.message("INSP.new.style.generics.assignment.expressions.not.allowed"))
      }
    }

    override fun visitPyFunction(node: PyFunction) {
      val returnTypeAnnotation = node.annotation
      val typeParameterList = node.typeParameterList

      if (typeParameterList != null) {
        reportOldStyleTypeVarsUsage(node.parameterList,
                                    PyPsiBundle.message("INSP.new.style.generics.mixing.old.style.and.new.style.type.vars.not.allowed"))
        node.parameterList
          .parameters
          .filterIsInstance<PyNamedParameter>()
          .mapNotNull { it.annotation }
          .forEach { annotation ->
            reportAssignmentExpressions(annotation, PyPsiBundle.message("INSP.new.style.generics.assignment.expressions.not.allowed"))
          }
        if (returnTypeAnnotation != null) {
          reportOldStyleTypeVarsUsage(returnTypeAnnotation,
                                      PyPsiBundle.message("INSP.new.style.generics.mixing.old.style.and.new.style.type.vars.not.allowed"))
          reportAssignmentExpressions(returnTypeAnnotation,
                                      PyPsiBundle.message("INSP.new.style.generics.assignment.expressions.not.allowed"))
        }
      }
    }

    override fun visitPyClass(node: PyClass) {
      if (node.typeParameterList != null) {
        val superClassExpressionList = node.superClassExpressionList
        if (superClassExpressionList != null) {
          node.superClassExpressions.forEach { ancestor ->
            val reference = if (ancestor is PySubscriptionExpression) ancestor.operand else ancestor
            val type = myTypeEvalContext.getType(reference)
            if (type is PyClassLikeType) {
              val qName = type.classQName
              if (PyTypingTypeProvider.GENERIC == qName) {
                registerProblem(ancestor,
                                PyPsiBundle.message("INSP.new.style.generics.classes.with.type.param.list.should.not.extend.generic"),
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
              }
              if (PyTypingTypeProvider.PROTOCOL == qName || PyTypingTypeProvider.PROTOCOL_EXT == qName) {
                if (ancestor is PySubscriptionExpression) {
                  registerProblem(ancestor.indexExpression,
                                  PyPsiBundle.message("INSP.new.style.generics.extending.protocol.does.not.need.parameterization"),
                                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                }
              }
            }
          }
          reportOldStyleTypeVarsUsage(superClassExpressionList,
                                      PyPsiBundle.message("INSP.new.style.generics.mixing.old.style.and.new.style.type.vars.not.allowed"))
          reportAssignmentExpressions(superClassExpressionList,
                                      PyPsiBundle.message("INSP.new.style.generics.assignment.expressions.not.allowed"))
        }
      }
    }

    private fun reportOldStyleTypeVarsUsage(element: PsiElement, @InspectionMessage message: String) {
      PsiTreeUtil.findChildrenOfAnyType(element, false, PyReferenceExpression::class.java)
        .associateWith { Ref.deref(PyTypingTypeProvider.getType(it, myTypeEvalContext)) }
        // if declarationElement a.k.a target expression is null then it most likely resolves to type parameter
        .filter { (_, v) -> v is PyTypeVarType && v.declarationElement != null }
        .forEach { (k, _) ->
          registerProblem(k, message,
                          ProblemHighlightType.GENERIC_ERROR)
        }
    }

    private fun reportAssignmentExpressions(element: PsiElement, @InspectionMessage message: String) {
      PsiTreeUtil.findChildrenOfAnyType(element, false, PyAssignmentExpression::class.java)
        .forEach { assignmentExpr ->
          registerProblem(assignmentExpr, message,
                          ProblemHighlightType.GENERIC_ERROR)
        }
    }
  }
}