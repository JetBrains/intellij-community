package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typeHints.PyTypeHintFile
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PySelfType
import com.jetbrains.python.psi.types.PyTypeParameterType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyClassVarInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder,
                                                                                              PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyTargetExpression(node: PyTargetExpression) {
        checkClassVarReassignment(node)
        checkClassVarDeclaration(node)
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      if (!PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext)) return
      if (!resolvesToClassVar(node)) return

      var classVar: PyExpression = node
      val classVarParent = classVar.parent
      // promote analyzing ClassVar to the subscription expression wrapping it
      if (classVarParent is PySubscriptionExpression && classVarParent.operand == classVar) {
        classVar = classVarParent
        checkClassVarParameterization(classVar)
      }

      val parent = classVar.parent

      if (parent is PyAssignmentStatement) {
        registerProblem(classVar, PyPsiBundle.message("INSP.class.var.is.not.allowed.here"))
        return
      }

      val reportNestedClassVar = when (parent) {
        is PyAnnotation -> false
        is PyExpressionStatement -> parent.parent !is PyTypeHintFile // type comment
        is PyTupleExpression -> !isWrappedWithTypingAnnotated(parent)
        else -> true
      }

      if (reportNestedClassVar) {
        registerProblem(classVar,
                        PyPsiBundle.message("INSP.class.var.can.not.be.nested"))
      }
    }

    private fun isWrappedWithTypingAnnotated(tupleExpression: PyTupleExpression): Boolean {
      val parentOfTuple = tupleExpression.parent
      return (parentOfTuple is PySubscriptionExpression
              && PyTypingTypeProvider.resolveToQualifiedNames(parentOfTuple.operand, myTypeEvalContext)
                .any { it == PyTypingTypeProvider.ANNOTATED || it == PyTypingTypeProvider.ANNOTATED_EXT })
    }

    private fun checkClassVarParameterization(node: PySubscriptionExpression) {
      val operand = node.operand
      if (resolvesToClassVar(operand)) {
        val indexExpression = node.indexExpression
        if (indexExpression != null) {
          if (indexExpression is PyTupleExpression && indexExpression.elements.size > 1) {
            registerProblem(indexExpression, PyPsiBundle.message("INSP.class.var.can.only.be.parameterized.with.one.type"))
          }
          else {
            val typeRef = PyTypingTypeProvider.getType(indexExpression, myTypeEvalContext)
            if (typeRef == null) {
              registerProblem(indexExpression, PyPsiBundle.message("INSP.class.var.not.a.valid.type"))
            }
            val references = PsiTreeUtil.findChildrenOfAnyType(indexExpression, false, PyReferenceExpression::class.java)
            references.forEach { reference ->
              val referenceType = Ref.deref(PyTypingTypeProvider.getType(reference, myTypeEvalContext))
              if (referenceType is PyTypeParameterType && referenceType !is PySelfType) {
                registerProblem(reference,
                                PyPsiBundle.message("INSP.class.var.can.not.include.type.variables"))
              }
            }
          }
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
      val scopeOwner = ScopeUtil.getScopeOwner(target)
      if (scopeOwner is PyClass) {
        checkInheritedClassClassVarReassignmentOnClassLevel(target, scopeOwner)
      }
      else {
        if (target.isClassVar()) {
          registerProblem(target.typeComment ?: target.annotation?.value,
                          PyPsiBundle.message("INSP.class.var.can.be.used.only.in.class.body"))
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