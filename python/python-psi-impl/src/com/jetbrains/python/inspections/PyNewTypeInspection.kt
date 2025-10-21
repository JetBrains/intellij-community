package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyClassImpl
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.*

class PyNewTypeInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyTargetExpression(node: PyTargetExpression) {
        val assignedValue = node.findAssignedValue()
        if (assignedValue !is PyCallExpression) return

        val callee = assignedValue.callee as? PyReferenceExpression ?: return
        val resolved = callee.followAssignmentsChain(resolveContext).element ?: return
        val isNewTypeCall = resolved is PyQualifiedNameOwner && resolved.qualifiedName == PyTypingTypeProvider.NEW_TYPE

        if (isNewTypeCall) {
          val newTypeName = PyResolveUtil.resolveStrArgument(assignedValue, 0, "name")
          val targetName = node.name
          if (targetName != newTypeName) {
            registerProblem(node.nameIdentifier,
                            PyPsiBundle.message("INSP.NAME.new.type.variable.name.does.not.match.new.type.name", targetName, newTypeName))
          }

          val typeExpr = PyPsiUtils.flattenParens(assignedValue.getArgument(1, "tp", PyExpression::class.java))
          if (typeExpr != null) {
            val type = Ref.deref(PyTypingTypeProvider.getType(typeExpr, myTypeEvalContext))
            if (type !is PyClassType) {
              registerProblem(typeExpr, PyPsiBundle.message("INSP.NAME.new.type.expected.class"))
            }
            else if (type is PyCollectionType && type.elementTypes.any { it is PyTypeVarType && it.scopeOwner == null }) {
              registerProblem(typeExpr, PyPsiBundle.message("INSP.NAME.new.type.new.type.cannot.be.generic"))
            }
            else if (type is PyLiteralType) {
              registerProblem(typeExpr, PyPsiBundle.message("INSP.NAME.new.type.new.type.cannot.be.used.with", type.name))
            }
            else if (type is PyTypedDictType) {
              registerProblem(typeExpr, PyPsiBundle.message("INSP.NAME.new.type.new.type.cannot.be.used.with", "TypedDict"))
            }
          }
        }
      }

      override fun visitPyClass(node: PyClass) {
        for (superClassExpression in PyClassImpl.getUnfoldedSuperClassExpressions(node)) {
          val superClassType = myTypeEvalContext.getType(superClassExpression)
          if (superClassType is PyTypingNewType) {
            registerProblem(superClassExpression,
                            PyPsiBundle.message("INSP.NAME.new.type.cannot.be.subclassed", superClassType.name),
                            ProblemHighlightType.GENERIC_ERROR)
          }
        }
      }
    }
  }
}