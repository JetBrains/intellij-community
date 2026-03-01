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
import com.jetbrains.python.ast.PyAstTypeParameter
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyAssignmentExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.PyTypeParameterList
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyTypeParameterType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyNewStyleGenericSyntaxInspection : PyInspection() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = Visitor(holder,
                                 PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyTypeParameter(typeParameter: PyTypeParameter) {
      val boundExpression = typeParameter.boundExpression
      val defaultExpression = typeParameter.defaultExpression

      boundExpression?.accept(object : PyRecursiveElementVisitor() {
        override fun visitPyElement(node: PyElement) {
          if (!(node is PyParenthesizedExpression && node === boundExpression) &&
              !(node is PyTupleExpression && node.parent === boundExpression)
          ) {
            if (node is PyExpression) {
              if (!PyTypeHintsInspection.Helper.isValidTypeHint(node, myTypeEvalContext)) {
                registerProblem(
                  node,
                  PyPsiBundle.message("INSP.type.hints.invalid.type.expression"),
                )
              }
              if (node is PyReferenceExpression) {
                node.getTypeParameterType()?.let {
                  registerProblem(node,
                                  PyPsiBundle.message("INSP.new.style.generics.are.not.allowed.inside.type.param.bounds"),
                                  ProblemHighlightType.GENERIC_ERROR)
                }
              }
            }
          }
          super.visitPyElement(node)
        }
      })

      if (defaultExpression != null) {
        val scopeOwner = ScopeUtil.getScopeOwner(typeParameter)
        if (scopeOwner == null) return

        defaultExpression.accept(object : PyRecursiveElementVisitor() {
          override fun visitPyReferenceExpression(node: PyReferenceExpression) {
            node.getTypeParameterType()?.let {
              if (scopeOwner != it.scopeOwner || it.declarationElement is PyTargetExpression) {
                registerProblem(node,
                                PyPsiBundle.message("INSP.new.style.type.parameter.out.of.scope", node.name),
                                ProblemHighlightType.WARNING)
              }
            }
            super.visitPyReferenceExpression(node)
          }
        })
      }
    }

    override fun visitPyTypeParameterList(node: PyTypeParameterList) {
      val typeParameters = node.typeParameters
      var lastIsDefault = false
      var lastIsTypeVarTuple = false
      for (typeParameter in typeParameters) {
        if (typeParameter.defaultExpressionText != null) {
          lastIsDefault = true
          if (lastIsTypeVarTuple && typeParameter.kind == PyAstTypeParameter.Kind.TypeVar) {
            registerProblem(typeParameter,
                            PyPsiBundle.message("INSP.type.hints.default.type.var.cannot.follow.type.var.tuple"),
                            ProblemHighlightType.GENERIC_ERROR)
          }
        }
        else if (lastIsDefault) {
          registerProblem(typeParameter,
                          PyPsiBundle.message("INSP.type.hints.non.default.type.vars.cannot.follow.defaults"),
                          ProblemHighlightType.GENERIC_ERROR)
        }
        lastIsTypeVarTuple = typeParameter.kind == PyAstTypeParameter.Kind.TypeVarTuple
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
      element.accept(object : PyRecursiveElementVisitor() {
        override fun visitPyReferenceExpression(node: PyReferenceExpression) {
          node.getTypeParameterType()?.let {
            if (it.declarationElement is PyTargetExpression
                && ScopeUtil.getScopeOwner(it.declarationElement) !is PyTypeAliasStatement
            ) {
              registerProblem(node, message, ProblemHighlightType.GENERIC_ERROR)
            }
          }
        }
      })
    }

    private fun reportAssignmentExpressions(element: PsiElement, @InspectionMessage message: String) {
      PsiTreeUtil.findChildrenOfAnyType(element, false, PyAssignmentExpression::class.java)
        .forEach { assignmentExpr ->
          registerProblem(assignmentExpr, message,
                          ProblemHighlightType.GENERIC_ERROR)
        }
    }

    private fun PyReferenceExpression.getTypeParameterType(): PyTypeParameterType? {
      return Ref.deref(PyTypingTypeProvider.getType(this, myTypeEvalContext)) as? PyTypeParameterType
    }
  }
}