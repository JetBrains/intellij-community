// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMonkeypatch

import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

private const val MONKEYPATCH_FQN = "_pytest.monkeypatch.MonkeyPatch"

/**
 * Returns `true` if [callExpr] is a call to `monkeypatch.setattr(...)` or `monkeypatch.delattr(...)`.
 *
 * Detection strategy:
 * 1. The callee must be `<qualifier>.setattr` or `<qualifier>.delattr`.
 * 2. The qualifier is identified as a monkeypatch fixture by either:
 *    - Type: its type is `_pytest.monkeypatch.MonkeyPatch`
 *    - Name: it resolves to a parameter named `monkeypatch` (a reserved pytest fixture)
 */
internal fun isMonkeypatchAttrCall(callExpr: PyCallExpression, methodName: String, context: TypeEvalContext): Boolean {
  val callee = callExpr.callee as? PyQualifiedExpression ?: return false
  if (callee.name != methodName) return false

  val qualifier = callee.qualifier ?: return false

  // Check by type
  val qualifierType = context.getType(qualifier)
  if (qualifierType is PyClassType && qualifierType.pyClass.qualifiedName == MONKEYPATCH_FQN) {
    return true
  }

  // Check by name: qualifier resolves to a parameter named "monkeypatch" in a test function
  if (qualifier is PyReferenceExpression && qualifier.name == "monkeypatch") {
    val resolveContext = PyResolveContext.defaultContext(context)
    val resolved = qualifier.followAssignmentsChain(resolveContext).element
    if (resolved is PyNamedParameter && resolved.name == "monkeypatch") {
      val func = resolved.parent?.parent as? PyFunction
      if (func != null) return true
    }
  }

  return false
}
