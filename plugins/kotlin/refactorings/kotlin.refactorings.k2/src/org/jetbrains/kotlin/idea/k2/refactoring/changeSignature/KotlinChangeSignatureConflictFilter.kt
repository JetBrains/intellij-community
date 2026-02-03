// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference

interface KotlinChangeSignatureConflictFilter {
    fun skipUsage(parameter: PsiNamedElement, reference: PsiReference): Boolean

    companion object {
        val EP_NAME = ExtensionPointName.create<KotlinChangeSignatureConflictFilter>("org.jetbrains.kotlin.changeSignatureConflictFilter")
    }
}