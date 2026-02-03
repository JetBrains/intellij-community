// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.targetApiImpl

import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.api.SafeDeleteTarget
import com.intellij.refactoring.safeDelete.api.SafeDeleteTargetProvider
import org.jetbrains.kotlin.psi.KtElement

@Suppress("unused")
class KotlinSafeDeleteTargetProvider : SafeDeleteTargetProvider {
    override fun createSafeDeleteTarget(element: PsiElement): SafeDeleteTarget? {
        return (element as? KtElement)?.let {
          KotlinSafeDeleteTarget(it)
        }
    }
}