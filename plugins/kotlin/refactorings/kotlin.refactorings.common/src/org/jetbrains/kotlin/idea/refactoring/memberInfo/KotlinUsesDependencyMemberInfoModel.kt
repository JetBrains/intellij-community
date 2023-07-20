// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.classMembers.MemberInfoBase
import com.intellij.refactoring.classMembers.MemberInfoModel
import com.intellij.refactoring.util.classMembers.UsesDependencyMemberInfoModel
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

open class KotlinUsesDependencyMemberInfoModel<T : KtNamedDeclaration, M : MemberInfoBase<T>>(
    klass: KtClassOrObject,
    superClass: PsiNamedElement?,
    recursive: Boolean
) : UsesDependencyMemberInfoModel<T, PsiNamedElement, M>(klass, superClass, recursive) {
    override fun doCheck(memberInfo: M, problem: Int): Int {
        val member = memberInfo.member
        val container = member.containingClassOrObject
        if (problem == MemberInfoModel.ERROR
            && container is KtObjectDeclaration
            && container.isCompanion()
            && container.containingClassOrObject == myClass
        ) return MemberInfoModel.WARNING

        return problem
    }
}