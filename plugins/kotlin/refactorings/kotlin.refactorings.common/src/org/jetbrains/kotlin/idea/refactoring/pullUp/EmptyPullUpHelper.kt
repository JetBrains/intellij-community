// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiSubstitutor
import com.intellij.refactoring.classMembers.MemberInfoBase
import com.intellij.refactoring.memberPullUp.PullUpHelper

object EmptyPullUpHelper : PullUpHelper<MemberInfoBase<PsiMember>> {
    override fun postProcessMember(member: PsiMember): Unit = Unit
    override fun moveFieldInitializations(movedFields: LinkedHashSet<PsiField>): Unit = Unit
    override fun encodeContextInfo(info: MemberInfoBase<PsiMember>): Unit = Unit
    override fun setCorrectVisibility(info: MemberInfoBase<PsiMember>): Unit = Unit
    override fun move(info: MemberInfoBase<PsiMember>, substitutor: PsiSubstitutor): Unit = Unit
    override fun updateUsage(element: PsiElement): Unit = Unit
}
