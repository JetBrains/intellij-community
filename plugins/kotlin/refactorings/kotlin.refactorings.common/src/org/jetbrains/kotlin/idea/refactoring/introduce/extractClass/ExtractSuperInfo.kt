// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass

import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.DocCommentPolicy
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.psi.KtClassOrObject

@ApiStatus.Internal
data class ExtractSuperInfo(
    val originalClass: KtClassOrObject,
    val memberInfos: Collection<KotlinMemberInfo>,
    val targetParent: PsiElement,
    val targetFileName: String,
    val newClassName: String,
    val isInterface: Boolean,
    val docPolicy: DocCommentPolicy
)
