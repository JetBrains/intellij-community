// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.icons.AllIcons
import com.intellij.refactoring.classMembers.MemberInfoModel
import com.intellij.refactoring.ui.AbstractMemberSelectionTable
import com.intellij.ui.RowIcon
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import javax.swing.Icon

class KotlinMemberSelectionTable(
    memberInfos: List<KotlinMemberInfo>,
    memberInfoModel: MemberInfoModel<KtNamedDeclaration, KotlinMemberInfo>?,
    @Nls abstractColumnHeader: String?
) : AbstractMemberSelectionTable<KtNamedDeclaration, KotlinMemberInfo>(memberInfos, memberInfoModel, abstractColumnHeader) {
    override fun getAbstractColumnValue(memberInfo: KotlinMemberInfo): Any? {
        if (memberInfo.isStatic || memberInfo.isCompanionMember) return null

        val member = memberInfo.member
        if (member !is KtNamedFunction && member !is KtProperty && member !is KtParameter) return null

        if (member.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
            myMemberInfoModel.isFixedAbstract(memberInfo)?.let { return it }
        }
        if (myMemberInfoModel.isAbstractEnabled(memberInfo)) return memberInfo.isToAbstract
        return myMemberInfoModel.isAbstractWhenDisabled(memberInfo)
    }

    override fun isAbstractColumnEditable(rowIndex: Int): Boolean {
        val memberInfo = myMemberInfos[rowIndex]

        if (memberInfo.isStatic) return false

        val member = memberInfo.member
        if (member !is KtNamedFunction && member !is KtProperty && member !is KtParameter) return false

        if (member.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
            myMemberInfoModel.isFixedAbstract(memberInfo)?.let { return false }
        }

        return memberInfo.isChecked && myMemberInfoModel.isAbstractEnabled(memberInfo)
    }

    override fun setVisibilityIcon(memberInfo: KotlinMemberInfo, icon: RowIcon) {
        icon.setIcon(KotlinIconProvider.getVisibilityIcon(memberInfo.member.modifierList), 1)
    }

    override fun getOverrideIcon(memberInfo: KotlinMemberInfo): Icon? {
        val defaultIcon = EMPTY_OVERRIDE_ICON

        val member = memberInfo.member
        if (member !is KtNamedFunction && member !is KtProperty && member !is KtParameter) return defaultIcon

        return when (memberInfo.overrides) {
            true -> AllIcons.General.OverridingMethod
            false -> AllIcons.General.ImplementingMethod
            else -> defaultIcon
        }
    }
}