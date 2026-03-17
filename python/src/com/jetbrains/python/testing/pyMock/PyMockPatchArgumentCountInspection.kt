// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Reports a mismatch between the number of `@patch`/`@patch.object` decorators (that inject
 * mock parameters) and the number of extra parameters in the decorated function.
 *
 * A decorator injects a parameter unless it has an explicit `new` argument (keyword or positional).
 * Functions with `*args` or `**kwargs` are skipped since they accept arbitrary parameters.
 */
class PyMockPatchArgumentCountInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = object : PyInspectionVisitor(holder, getContext(session)) {
    override fun visitElement(element: PsiElement) {
      if (element !is PyFunction) return
      checkPatchArgumentCount(element, holder, myTypeEvalContext)
    }
  }
}

private fun checkPatchArgumentCount(func: PyFunction, holder: ProblemsHolder, context: TypeEvalContext) {
  val decorators = func.decoratorList?.decorators ?: return

  val injectingCount = decorators.count { dec ->
    isPatchOrPatchObjectCall(dec, context) && !hasNewArgument(dec, context)
  }
  if (injectingCount == 0) return

  val params = func.parameterList.parameters

  // Skip if any parameter uses *args or **kwargs — they absorb arbitrary arguments
  if (params.any { !it.isSelf && it.text.startsWith("*") }) return

  // Count non-self parameters; these are the ones that should match injecting decorators
  val extraParamCount = params.count { !it.isSelf }

  val diff = injectingCount - extraParamCount
  if (diff > 0) {
    holder.registerProblem(
      func.parameterList,
      PyPsiBundle.message("INSP.mock.patch.too.few.params", diff),
    )
  }
  else if (diff < 0) {
    holder.registerProblem(
      func.parameterList,
      PyPsiBundle.message("INSP.mock.patch.too.many.params", -diff),
    )
  }
}
