package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyGenericType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyClassVarInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder,
                                                                                              PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyTargetExpression(node: PyTargetExpression) {
      super.visitPyTargetExpression(node)
      if (node.hasAssignedValue()) {
        if (node.isQualified) {
          checkClassVarReassignment(node)
        }
        else {
          checkClassVarDeclaration(node)
        }
      }
      if (node.isClassVar()) {
        val typeExpression = node.typeCommentAnnotation?.let { toExpression(it, node) }
                             ?: node.annotation?.value

        if (typeExpression != null) {
          checkDoesNotIncludeTypeVars(typeExpression, node.typeComment)
        }
      }
    }

    override fun visitPyNamedParameter(node: PyNamedParameter) {
      super.visitPyNamedParameter(node)
      if (node.isClassVar()) {
        registerProblem(node.annotation?.value ?: node.typeComment,
                        PyPsiBundle.message("INSP.class.var.can.not.be.used.in.annotations.for.function.parameters"))
      }
    }

    override fun visitPyFunction(node: PyFunction) {
      super.visitPyFunction(node)
      PyTypingTypeProvider.getReturnTypeAnnotation(node, myTypeEvalContext)?.let {
        if (resolvesToClassVar(if (it is PySubscriptionExpression) it.operand else it)) {
          registerProblem(node.typeComment ?: node.annotation?.value,
                          PyPsiBundle.message("INSP.class.var.can.not.be.used.in.annotation.for.function.return.value"))
        }
      }
    }

    private fun checkClassVarDeclaration(target: PyTargetExpression) {
      when (val scopeOwner = ScopeUtil.getScopeOwner(target)) {
        is PyFile -> {
          if (PyUtil.isTopLevel(target) && target.isClassVar()) {
            registerProblem(target.typeComment ?: target.annotation?.value,
                            PyPsiBundle.message("INSP.class.var.can.be.used.only.in.class.body"))
          }
        }
        is PyFunction -> {
          if (target.isClassVar()) {
            registerProblem(target.typeComment ?: target.annotation?.value,
                            PyPsiBundle.message("INSP.class.var.can.not.be.used.in.function.body"))
          }
        }
        is PyClass -> {
          checkInheritedClassClassVarReassignmentOnClassLevel(target, scopeOwner)
        }
      }
    }

    private fun checkClassVarReassignment(target: PyTargetExpression) {
      val qualifierType = target.qualifier?.let { myTypeEvalContext.getType(it) }
      if (qualifierType is PyClassType && !qualifierType.isDefinition) {
        checkInstanceClassVarReassignment(target, qualifierType.pyClass)
      }
    }

    private fun checkInheritedClassClassVarReassignmentOnClassLevel(target: PyTargetExpression, cls: PyClass) {
      val name = target.name ?: return
      for (ancestor in cls.getAncestorClasses(myTypeEvalContext)) {
        val ancestorClassAttribute = ancestor.findClassAttribute(name, false, myTypeEvalContext)
        if (ancestorClassAttribute != null && ancestorClassAttribute.hasExplicitType() && target.hasExplicitType()) {
          if (ancestorClassAttribute.isClassVar() && !target.isClassVar()) {
            registerProblem(target, PyPsiBundle.message("INSP.class.var.can.not.override.class.variable", name, ancestor.name))
            break
          }
          if (!ancestorClassAttribute.isClassVar() && target.isClassVar()) {
            registerProblem(target, PyPsiBundle.message("INSP.class.var.can.not.override.instance.variable", name, ancestor.name))
            break
          }
        }
      }
    }

    private fun checkInstanceClassVarReassignment(target: PyQualifiedExpression, cls: PyClass) {
      val name = target.name ?: return
      for (ancestor in listOf(cls) + cls.getAncestorClasses(myTypeEvalContext)) {
        val inheritedClassAttribute = ancestor.findClassAttribute(name, false, myTypeEvalContext)
        if (inheritedClassAttribute != null && inheritedClassAttribute.isClassVar()) {
          registerProblem(target, PyPsiBundle.message("INSP.class.var.can.not.be.assigned.to.instance", name))
          return
        }
      }
    }

    private fun checkDoesNotIncludeTypeVars(expression: PyExpression?, typeComment: PsiComment?): Boolean {
      var element = expression

      while (element is PySubscriptionExpression) {
        element = element.indexExpression
      }

      when (element) {
        is PyTupleExpression -> {
          element.forEach { tupleElement ->
            val reported = checkDoesNotIncludeTypeVars(tupleElement, typeComment)
            if (reported && typeComment != null) {
              return true
            }
          }
        }
        is PyReferenceExpression -> {
          val type = PyTypingTypeProvider.getType(element, myTypeEvalContext)?.get()
          if (type is PyGenericType) {
            registerProblem(typeComment ?: element,
                            PyPsiBundle.message("INSP.class.var.can.not.include.type.variables"))
            return true
          }
        }
      }
      return false
    }

    private fun toExpression(contents: String, anchor: PsiElement): PyExpression? {
      val file = FileContextUtil.getContextFile(anchor) ?: return null
      return PyUtil.createExpressionFromFragment(contents, file)
    }

    private fun PyTargetExpression.hasExplicitType(): Boolean =
      annotationValue != null || typeCommentAnnotation != null

    private fun <T> T.isClassVar(): Boolean where T : PyAnnotationOwner, T : PyTypeCommentOwner =
      PyTypingTypeProvider.isClassVar(this, myTypeEvalContext)

    private fun resolvesToClassVar(expression: PyExpression): Boolean {
      return expression is PyReferenceExpression &&
             PyTypingTypeProvider.resolveToQualifiedNames(expression, myTypeEvalContext)
               .any { it == PyTypingTypeProvider.CLASS_VAR }
    }

  }
}