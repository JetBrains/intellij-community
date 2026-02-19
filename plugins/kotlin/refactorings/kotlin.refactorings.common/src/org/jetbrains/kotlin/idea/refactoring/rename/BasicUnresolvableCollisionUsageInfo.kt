// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo

class BasicUnresolvableCollisionUsageInfo(
    element: PsiElement,
    referencedElement: PsiElement,
    @NlsContexts.DialogMessage private val _description: String
) : UnresolvableCollisionUsageInfo(element, referencedElement) {
    override fun getDescription() = _description
}
