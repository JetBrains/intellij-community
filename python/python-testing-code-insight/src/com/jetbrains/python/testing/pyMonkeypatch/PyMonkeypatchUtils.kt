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
import com.jetbrains.python.testing.pyTestFixtures.isFixture
import com.jetbrains.python.testing.pyTestFixtures.reservedFixturesSet

private const val MONKEYPATCH_FQN = "_pytest.monkeypatch.MonkeyPatch"

/**
 * Returns `true` if [callExpr] is a call to `monkeypatch.setattr(...)` or `monkeypatch.delattr(...)`.
 *
 * Detection strategy:
 * 1. The callee must be `<qualifier>.setattr` or `<qualifier>.delattr`.
 * 2. The qualifier is identified as a monkeypatch fixture by either:
 *    - Type: its type is `_pytest.monkeypatch.MonkeyPatch`
 *    - Fixture: it resolves to a parameter recognized as a pytest fixture
 *    - Reserved name: it resolves to a parameter named `monkeypatch` (a known pytest built-in fixture)
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

  // Check by fixture resolution
  if (qualifier is PyReferenceExpression) {
    val resolveContext = PyResolveContext.defaultContext(context)
    val resolved = qualifier.followAssignmentsChain(resolveContext).element
    if (resolved is PyNamedParameter) {
      return resolved.isFixture(context)
    }
  }

  return false
}
