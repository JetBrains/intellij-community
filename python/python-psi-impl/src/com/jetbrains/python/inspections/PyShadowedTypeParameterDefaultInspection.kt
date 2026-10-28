package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Reports a PEP 696 type parameter default (e.g. `int` in `def f[T = int]`) that can never be used because a parameter
 * of type `T` has a default value. A call then always solves `T`: an argument solves it, or the parameter default does
 * (see [PyCallExpressionHelper.mapArgumentsWithDefaults]).
 *
 * Example:
 * ```
 * def f[T = int](a: T = 0) -> T: ...  # `int` is never used: `f()` yields `Literal[0]`, `f(x)` yields the type of `x`
 * ```
 */
class PyShadowedTypeParameterDefaultInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val context = PyInspectionVisitor.getContext(session)
    if (context.usesExternalTypeEngine) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return Visitor(holder, context)
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyFunction(node: PyFunction) {
      super.visitPyFunction(node)

      val typeParameters = node.typeParameterList?.typeParameters ?: return
      val defaultedTypeParameters = typeParameters.filter { it.defaultExpression != null }
      if (defaultedTypeParameters.isEmpty()) return

      // Only a parameter of type `T` itself pins `T`. In `T | str`, an argument of type `str` leaves `T` unsolved.
      val pinnedTypeParameterNames = HashSet<String>()
      for (parameter in node.parameterList.parameters) {
        val namedParameter = parameter as? PyNamedParameter ?: continue
        if (namedParameter.isPositionalContainer || namedParameter.isKeywordContainer) continue
        val defaultValueText = namedParameter.defaultValueText ?: continue
        if (defaultValueText == PyNames.ELLIPSIS && PyCallExpressionHelper.isPlaceholderEllipsisDefault(namedParameter, myTypeEvalContext)) {
          continue
        }
        val parameterType = PyCallableParameterImpl.psi(namedParameter).getArgumentType(myTypeEvalContext)
        if (parameterType is PyTypeVarType && parameterType.scopeOwner == node) {
          pinnedTypeParameterNames.add(parameterType.name)
        }
      }
      if (pinnedTypeParameterNames.isEmpty()) return

      for (typeParameter in defaultedTypeParameters) {
        val name = typeParameter.name ?: continue
        if (name !in pinnedTypeParameterNames) continue
        val defaultExpression = typeParameter.defaultExpression ?: continue
        registerProblem(defaultExpression,
                        PyPsiBundle.message("INSP.shadowed.type.parameter.default", name),
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL)
      }
    }
  }
}
