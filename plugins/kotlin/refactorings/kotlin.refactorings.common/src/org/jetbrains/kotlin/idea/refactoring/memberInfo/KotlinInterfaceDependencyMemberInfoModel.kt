// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.openapi.util.NlsSafe
import com.intellij.refactoring.classMembers.DependencyMemberInfoModel
import com.intellij.refactoring.classMembers.MemberInfoBase
import com.intellij.refactoring.classMembers.MemberInfoModel
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.utils.ifEmpty

class KotlinInterfaceDependencyMemberInfoModel<T : KtNamedDeclaration, M : MemberInfoBase<T>>(
    aClass: KtClassOrObject
) : DependencyMemberInfoModel<T, M>(KotlinInterfaceMemberDependencyGraph<T, M>(aClass), MemberInfoModel.WARNING) {
    init {
        setTooltipProvider { memberInfo ->
            val dependencies = myMemberDependencyGraph.getDependenciesOf(memberInfo.member).ifEmpty { return@setTooltipProvider null }
            @NlsSafe
            val text = buildString {
                append(KotlinBundle.message("interface.member.dependency.required.by.interfaces", dependencies.size))
                append(" ")
                dependencies.joinTo(this) { it.name ?: "" }
            }
            text
        }
    }

    override fun isCheckedWhenDisabled(member: M) = false

    override fun isFixedAbstract(member: M) = null
}