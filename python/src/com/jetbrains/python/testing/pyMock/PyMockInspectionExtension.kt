// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Suppresses false-positive warnings for mocks:
 *
 * - "unused parameter" on parameters injected by `@patch` and `@patch.object` decorators;
 * - "unresolved attribute" on an attribute or a return value of a mock with a spec, as in `MagicMock(spec=A).foo.bar`.
 *   With `spec` or `spec_set`, CPython creates `foo` without a spec, so every attribute of it is valid.
 */
internal class PyMockInspectionExtension : PyInspectionExtension() {
  override fun ignoreUnused(local: PsiElement, evalContext: TypeEvalContext): Boolean {
    if (local !is PyNamedParameter) return false
    val func = local.parent?.parent as? PyFunction ?: return false
    return getInjectingPatchDecorator(local, func, evalContext) != null
  }

  override fun ignoreUnresolvedMember(type: PyType, name: String, context: TypeEvalContext): Boolean =
    type is PyMockWithSpecType && type.kind == PyMockSpecKind.CHILD
}
