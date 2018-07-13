// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.impl.references.PyReferenceCustomTargetChecker

class PyTestFixtureTargetChecker : PyReferenceCustomTargetChecker {
  override fun isReferenceTo(reference: PsiReference, to: PsiElement): Boolean {
    val function = to as? PyFunction ?: return false
    val module = ModuleUtilCore.findModuleForPsiElement(to) ?: return false

    // reference is reference from param usage to param
    // param has reference to fixture

    if (function.isFixture() && isPyTestEnabled(module)) {
      val parameter = reference.resolve() as? PyNamedParameter ?: return false
      val ref = parameter.references.filterIsInstance<PyTextFixtureReference>().firstOrNull() ?: return false
      return ref.isReferenceTo(to)
    }
    return false
  }
}