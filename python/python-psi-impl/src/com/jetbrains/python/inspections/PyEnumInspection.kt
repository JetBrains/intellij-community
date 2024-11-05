package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext

class PyEnumInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyClass(node: PyClass) {
        val enumSuperClassWithMembers = node.getSuperClasses(myTypeEvalContext).find { isEnumClassWithMembers(it, myTypeEvalContext) }
        if (enumSuperClassWithMembers != null) {
          registerProblem(node.nameIdentifier, PyPsiBundle.message("INSP.enum.enum.class.is.final.and.cannot.be.subclassed", enumSuperClassWithMembers.name))
        }
        validateEnumMembers(node)
      }

      private fun validateEnumMembers(pyClass: PyClass) {
        if (!PyStdlibTypeProvider.isCustomEnum(pyClass, myTypeEvalContext)) return

        val declaredType = getDeclaredEnumMemberType(pyClass, myTypeEvalContext)
        val declaredTypeName = PythonDocumentationProvider.getVerboseTypeName(declaredType, myTypeEvalContext)

        for (attribute in pyClass.classAttributes) {
          val info = PyStdlibTypeProvider.getEnumAttributeInfo(attribute, myTypeEvalContext)
          if (info == null || !info.isMemberOrAlias) continue

          if (declaredType != null) {
            val type = info.assignedValueType
            val isAlias = type is PyLiteralType && type.pyClass == pyClass
            if (!isAlias) {
              val value = attribute.findAssignedValue()
              if (PyPsiUtils.flattenParens(value) is PyTupleExpression) {
                // TODO: > Type checkers may validate consistency between assigned tuple values and the constructor signature
              }
              else if (!PyTypeChecker.match(declaredType, type, myTypeEvalContext)) {
                val valueTypeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext)
                registerProblem(value, PyPsiBundle.message("INSP.enum.type.is.not.assignable.to.declared.type", valueTypeName, declaredTypeName))
              }
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

private fun isEnumClassWithMembers(pyClass: PyClass, context: TypeEvalContext): Boolean {
  if (PyStdlibTypeProvider.isCustomEnum(pyClass, context)) {
    return pyClass.classAttributes.any { PyStdlibTypeProvider.getEnumAttributeInfo(it, context)?.isMemberOrAlias == true } ||
           pyClass.nestedClasses.any { PyStdlibTypeProvider.isEnumMember(it, context) } ||
           pyClass.methods.any { PyStdlibTypeProvider.isEnumMember(it, context) }
  }
  return false
}

private fun getDeclaredEnumMemberType(enumClass: PyClass, context: TypeEvalContext): PyType? {
  val targetExpression = enumClass.findClassAttribute("_value_", true, context) ?: return null
  val annotationValue = PyTypingTypeProvider.getAnnotationValue(targetExpression, context) ?: return null
  return Ref.deref(PyTypingTypeProvider.getType(annotationValue, context))
}
