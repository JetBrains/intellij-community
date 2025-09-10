// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.psi.KtClassOrObject

@ApiStatus.Internal
interface KotlinExtractSuperConflictSearcher {
    fun collectConflicts(
        originalClass: KtClassOrObject,
        memberInfos: List<KotlinMemberInfo>,
        targetParent: PsiElement,
        newClassName: String,
        isExtractInterface: Boolean,
    ): MultiMap<PsiElement, String>

    companion object {
        @JvmStatic
        fun getInstance(): KotlinExtractSuperConflictSearcher = service<KotlinExtractSuperConflictSearcher>()
    }
}
