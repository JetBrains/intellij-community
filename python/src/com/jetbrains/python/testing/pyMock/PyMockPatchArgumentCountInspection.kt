// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * Reports a mismatch between the mocks that the `@patch` and `@patch.object` decorators inject and the parameters of the function.
 *
 * The inspection reports too few positional parameters for the mocks.
 * It reports extra parameters only in a test method or a setUp or tearDown method of a `unittest` `TestCase`.
 * `unittest` gives no other arguments to these methods.
 * pytest gives fixtures to the extra parameters, and other callers can give their own arguments.
 * The inspection skips a function with `*args` or `**kwargs`, because the function accepts any arguments.
 *
 * @see PyPatchInjection
 */
@ApiStatus.Internal
class PyMockPatchArgumentCountInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = object : PyInspectionVisitor(holder, getContext(session)) {
    override fun visitPyFunction(node: PyFunction) {
      checkPatchArgumentCount(node, holder, myTypeEvalContext)
    }
  }
}

private fun checkPatchArgumentCount(func: PyFunction, holder: ProblemsHolder, context: TypeEvalContext) {
  val namedParams = func.parameterList.parameters.mapNotNull { it.asNamed }
  if (namedParams.any { it.isPositionalContainer || it.isKeywordContainer }) return
  val injection = PyPatchInjection.of(func, context) ?: return

  val missing = injection.missingParameterCount
  if (missing > 0) {
    holder.registerProblem(func.parameterList, PyPsiBundle.message("INSP.mock.patch.too.few.params", missing))
    return
  }
  val extra = injection.extraParameterCount
  if (extra > 0) {
    holder.registerProblem(func.parameterList, PyPsiBundle.message("INSP.mock.patch.too.many.params", extra))
  }
}
