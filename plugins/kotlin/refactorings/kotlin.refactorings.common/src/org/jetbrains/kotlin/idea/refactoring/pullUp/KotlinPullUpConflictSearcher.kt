// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.psi.KtClassOrObject

@ApiStatus.Internal
interface KotlinPullUpConflictSearcher {
    /**
     * Executed on background thread under read action.
     */
    fun collectConflicts(
        sourceClass: KtClassOrObject,
        targetClass: PsiNamedElement,
        memberInfos: List<KotlinMemberInfo>,
    ): MultiMap<PsiElement, String>
    
    companion object {
        @JvmStatic
        fun getInstance(): KotlinPullUpConflictSearcher = service<KotlinPullUpConflictSearcher>()
    }
}