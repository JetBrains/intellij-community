package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.stubs.PyLiteralKind
import com.jetbrains.python.psi.types.PyTypeChecker.collectGenerics
import com.jetbrains.python.psi.types.PyTypeInferenceCspFactory
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Reports a PEP 696 type parameter default (e.g. `int` in `def f[T = int]`) that can never be used because a parameter
 * typed with that type variable has its own default value. In that case the parameter default always determines the
 * type variable (see PyTypeInferenceCspFactory), so the declared type-parameter default is unreachable.
 *
 * Example:
 * ```
 * def f[T = int](a: T = 0, b: T = 0) -> None: ...  # `int` is never used: `f()` yields `Literal[0]`, `f(x, x)` yields the type of `x`
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

      // Names of this function's type parameters that a parameter pins through its own (stub-safe) default value.
      // Only `None` and literal defaults are considered, since those are exactly the defaults that override the
      // declared PEP 696 default; an arbitrary-expression default keeps deferring to the declared default.
      val pinnedTypeParameterNames = HashSet<String>()
      for (parameter in node.parameterList.parameters) {
        val namedParameter = parameter as? PyNamedParameter ?: continue
        if (namedParameter.isPositionalContainer || namedParameter.isKeywordContainer) continue
        val defaultValue = namedParameter.defaultValue ?: continue
        val literalKind = PyLiteralKind.fromExpression(defaultValue) ?: continue
        // A `None` default is absorbed by an explicit `... | None` annotation, so it does not pin the type variable
        // there (mirrors PyTypeInferenceCspFactory) and the PEP 696 default is still used, hence not shadowed.
        if (literalKind == PyLiteralKind.NONE &&
            PyTypeInferenceCspFactory.parameterAnnotationAdmitsNone(namedParameter, myTypeEvalContext)) {
          continue
        }
        val parameterType = namedParameter.getArgumentType(myTypeEvalContext)
        for (typeVar in parameterType.collectGenerics(myTypeEvalContext).typeVars) {
          if (typeVar.scopeOwner == node) {
            pinnedTypeParameterNames.add(typeVar.name)
          }
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
