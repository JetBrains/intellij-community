// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.resolve.fromFoothold
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * Reports a `patch()` target without a dot.
 *
 * `patch("collections")` is invalid, because `patch` replaces an attribute of an object, and such a target
 * has no parent object. Patch an attribute of the module instead, for example `patch("collections.OrderedDict")`.
 *
 * `patch.dict()` and `patch.multiple()` take a different kind of target, so the inspection does not check them.
 */
@ApiStatus.Internal
class PyMockPatchTargetInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = object : PyInspectionVisitor(holder, getContext(session)) {
    override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
      checkPatchTarget(node, holder, myTypeEvalContext)
    }
  }
}

private fun checkPatchTarget(str: PyStringLiteralExpression, holder: ProblemsHolder, typeEvalContext: TypeEvalContext) {
  // Only check the target argument of a plain patch() call
  if (getPatchTargetCall(str, typeEvalContext)?.kind != PyPatchKind.PATCH) return
  // The value of an f-string is not known
  if (str.stringElements.any { it is PyFormattedStringElement }) return

  val target = str.stringValue
  if (target.isEmpty()) return

  // A target without a dot has no parent object. A dotted target is valid, also when it names a submodule.
  val qualifiedName = QualifiedName.fromDottedString(target)
  if (qualifiedName.componentCount != 1) return

  val context = fromFoothold(str).copyWithMembers()
  val resolved = resolveQualifiedName(qualifiedName, context).firstOrNull()
  val messageKey = if (resolved is PyFile || resolved is PsiDirectory) "INSP.mock.patch.target.is.module"
  else "INSP.mock.patch.target.has.no.dot"
  holder.registerProblem(str, PyPsiBundle.message(messageKey, target))
}
