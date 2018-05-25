// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestParametrized

import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyNamedParameter

/**
 * EP to skip "unused" inspection
 */
class PyTestParametrizedInspectionExtension : PyInspectionExtension() {

  override fun ignoreUnused(local: PsiElement): Boolean = local is PyNamedParameter && local.isParametrized()

}