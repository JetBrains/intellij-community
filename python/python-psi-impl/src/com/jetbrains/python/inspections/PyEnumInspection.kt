package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider.EnumAttributeInfo
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider.EnumAttributeKind
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.impl.PyClassImpl
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiUtil

class PyEnumInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyClass(node: PyClass) {
        validateSuperClasses(node)
        if (!PyStdlibTypeProvider.isCustomEnum(node, myTypeEvalContext)) return

        // Every check below reads the kind of each attribute, so read it once
        val attributes = node.classAttributes.mapNotNull { attribute ->
          PyStdlibTypeProvider.getEnumAttributeInfo(node, attribute, myTypeEvalContext)?.let { attribute to it }
        }
        validateEnumMembers(node, attributes)
        // A stub declares an enum but does not run its class body, so a definition error cannot occur there
        if (PyiUtil.isInsideStub(node)) return
        validateEnumBaseOrder(node)
      }

      private fun validateSuperClasses(node: PyClass) {
        for (superClassExpression in PyClassImpl.getUnfoldedSuperClassExpressions(node)) {
          val superClassType = myTypeEvalContext.getType(superClassExpression)
          if (superClassType is PyClassType) {
            val superClass = superClassType.pyClass
            if (PyStdlibTypeProvider.isFinalEnum(superClass, myTypeEvalContext)) {
              registerProblem(superClassExpression,
                              PyPsiBundle.problemMessage("INSP.enum.enum.class.is.final.and.cannot.be.subclassed", CodifiedParam.ofReference(superClass)),
                              ProblemHighlightType.GENERIC_ERROR)
            }
          }
        }
      }

      /**
       * The enum base must be the last base class, as in `class E(Mixin, Enum)`.
       */
      private fun validateEnumBaseOrder(pyClass: PyClass) {
        val superClassExpressions = PyClassImpl.getUnfoldedSuperClassExpressions(pyClass)
        val lastBaseClass = superClassExpressions.lastOrNull()?.let { getBaseClass(it) } ?: return
        // The enum machinery accepts any enum as the last base. Another order of two enums breaks the MRO instead.
        if (PyStdlibTypeProvider.isEnum(lastBaseClass, myTypeEvalContext)) return

        for (base in superClassExpressions.asReversed()) {
          val baseClass = getBaseClass(base) ?: continue
          if (!PyStdlibTypeProvider.isEnum(baseClass, myTypeEvalContext)) continue
          registerProblem(base,
                          PyPsiBundle.problemMessage("INSP.enum.enum.base.class.must.be.last", CodifiedParam.ofReference(baseClass)),
                          ProblemHighlightType.GENERIC_ERROR)
          return
        }
      }

      private fun getBaseClass(superClassExpression: PyExpression): PyClass? =
        (myTypeEvalContext.getType(superClassExpression) as? PyClassType)?.takeIf { it.isDefinition }?.pyClass

      private fun validateEnumMembers(pyClass: PyClass, attributes: List<Pair<PyTargetExpression, EnumAttributeInfo>>) {
        val declaredType = getDeclaredEnumMemberType(pyClass, myTypeEvalContext)

        for ((attribute, info) in attributes) {
          if (info.attributeKind == EnumAttributeKind.NONMEMBER) continue

          if (declaredType != null && info.attributeKind == EnumAttributeKind.MEMBER) {
            val value = attribute.findAssignedValue()
            if (PyPsiUtils.flattenParens(value) is PyTupleExpression) {
              // TODO: > Type checkers may validate consistency between assigned tuple values and the constructor signature
            }
            else if (!PyTypeChecker.match(declaredType, info.assignedValueType, myTypeEvalContext)) {
              registerProblem(value, PyPsiBundle.problemMessage("INSP.enum.type.is.not.assignable.to.declared.type",
                                                                CodifiedParam.ofType(info.assignedValueType, attribute, myTypeEvalContext),
                                                                CodifiedParam.ofType(declaredType, attribute, myTypeEvalContext, verbose = true)))
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
