// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * Suppresses false-positive "unused parameter" warnings on parameters injected by
 * `@patch` and `@patch.object` decorators.
 */
internal class PyMockInspectionExtension : PyInspectionExtension() {
  override fun ignoreUnused(local: PsiElement, evalContext: TypeEvalContext): Boolean {
    if (local !is PyNamedParameter) return false
    val func = local.parent?.parent as? PyFunction ?: return false
    return getInjectingPatchDecorator(local, func, evalContext) != null
  }
}
