// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.impl.references.PyReferenceCustomTargetChecker
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class PyTestFixtureTargetChecker : PyReferenceCustomTargetChecker {
  override fun isReferenceTo(reference: PsiReference, to: PsiElement): Boolean {
    val function = to as? PyFunction ?: return false
    val module = ModuleUtilCore.findModuleForPsiElement(to) ?: return false

    // reference is reference from param usage to param
    // param has reference to fixture

    if (function.isFixture() && isPyTestEnabled(module)) {
      val parameter = reference.resolve() as? PyNamedParameter ?: return false
      val ref = parameter.references.filterIsInstance<PyTestFixtureReference>().firstOrNull() ?: return false
      return ref.isReferenceTo(to)
    }
    return false
  }
}