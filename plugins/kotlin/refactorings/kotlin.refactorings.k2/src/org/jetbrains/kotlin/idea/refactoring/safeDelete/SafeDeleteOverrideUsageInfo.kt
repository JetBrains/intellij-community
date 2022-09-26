// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.JavaSafeDeleteDelegate
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteCustomUsageInfo
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteUsageInfo

class SafeDeleteOverrideUsageInfo(element: PsiElement, referencedElement: PsiElement) 
    : SafeDeleteUsageInfo(element, referencedElement), SafeDeleteCustomUsageInfo {
  override fun performRefactoring() {
    val element = element
    if (element != null) {
      JavaSafeDeleteDelegate.EP.forLanguage(element.language).removeOverriding(getElement()!!)
    }
  }
}