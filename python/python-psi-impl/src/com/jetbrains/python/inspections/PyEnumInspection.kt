package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.containers.isEmpty
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider.EnumAttributeKind
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.*

class PyEnumInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyClass(node: PyClass) {
        validateSuperClasses(node)
        validateEnumMembers(node)
      }

      private fun validateSuperClasses(node: PyClass) {
        for (superClassExpression in node.superClassExpressions) {
          val superClassType = myTypeEvalContext.getType(superClassExpression)
          if (superClassType is PyClassType) {
            val superClass = superClassType.pyClass
            if (PyStdlibTypeProvider.isCustomEnum(superClass, myTypeEvalContext)) {
              if (!PyStdlibTypeProvider.getEnumMembers(superClass, myTypeEvalContext).isEmpty()) {
                registerProblem(superClassExpression,
                                PyPsiBundle.message("INSP.enum.enum.class.is.final.and.cannot.be.subclassed", superClass.name),
                                ProblemHighlightType.GENERIC_ERROR)
              }
            }
          }
        }
      }

      private fun validateEnumMembers(pyClass: PyClass) {
        if (!PyStdlibTypeProvider.isCustomEnum(pyClass, myTypeEvalContext)) return

        val declaredType = getDeclaredEnumMemberType(pyClass, myTypeEvalContext)
        val declaredTypeName = PythonDocumentationProvider.getVerboseTypeName(declaredType, myTypeEvalContext)

        for (attribute in pyClass.classAttributes) {
          val info = PyStdlibTypeProvider.getEnumAttributeInfo(pyClass, attribute, myTypeEvalContext)
          if (info == null || info.attributeKind == EnumAttributeKind.NONMEMBER) continue

          if (declaredType != null && info.attributeKind == EnumAttributeKind.MEMBER) {
            val value = attribute.findAssignedValue()
            if (PyPsiUtils.flattenParens(value) is PyTupleExpression) {
              // TODO: > Type checkers may validate consistency between assigned tuple values and the constructor signature
            }
            else if (!PyTypeChecker.match(declaredType, info.assignedValueType, myTypeEvalContext)) {
              val valueTypeName = PythonDocumentationProvider.getTypeName(info.assignedValueType, myTypeEvalContext)
              registerProblem(value, PyPsiBundle.message("INSP.enum.type.is.not.assignable.to.declared.type", valueTypeName, declaredTypeName))
            }
          }

          val typeHint = PyTypingTypeProvider.getAnnotationValue(attribute, myTypeEvalContext)
          if (typeHint != null) {
            registerProblem(typeHint, PyPsiBundle.message("INSP.enum.type.annotations.are.not.allowed.for.enum.members"))
          }
        }
      }
    }
  }
}

private fun getDeclaredEnumMemberType(enumClass: PyClass, context: TypeEvalContext): PyType? {
  val targetExpression = enumClass.findClassAttribute("_value_", true, context) ?: return null
  val annotationValue = PyTypingTypeProvider.getAnnotationValue(targetExpression, context) ?: return null
  return Ref.deref(PyTypingTypeProvider.getType(annotationValue, context))
}
