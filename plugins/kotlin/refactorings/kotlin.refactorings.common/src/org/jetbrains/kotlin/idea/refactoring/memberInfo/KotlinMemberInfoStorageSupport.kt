// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.openapi.components.service
import com.intellij.psi.PsiNamedElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtNamedDeclaration

@ApiStatus.Internal
interface KotlinMemberInfoStorageSupport {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinMemberInfoStorageSupport = service()
    }

    fun memberConflict(member1: KtNamedDeclaration, member: KtNamedDeclaration): Boolean

    fun isInheritor(baseClass: PsiNamedElement, aClass: PsiNamedElement): Boolean
}
