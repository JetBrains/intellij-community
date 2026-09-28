// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runReadAction
import com.intellij.refactoring.classMembers.MemberInfoModel
import com.intellij.refactoring.ui.AbstractMemberSelectionTable
import com.intellij.ui.RowIcon
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import javax.swing.Icon

class KotlinMemberSelectionTable<I : KotlinMemberInfo>(
    memberInfos: List<I>,
    memberInfoModel: MemberInfoModel<KtElement, I>?,
    @Nls abstractColumnHeader: String?
) : AbstractMemberSelectionTable<KtElement, I>(memberInfos, memberInfoModel, abstractColumnHeader) {
    override fun getAbstractColumnValue(memberInfo: I): Any? {
        if (memberInfo.isStatic || memberInfo.isCompanionMember) return null

        val member = when (memberInfo) {
            is KotlinMemberInfo.Declaration -> memberInfo.declaration
            is KotlinMemberInfo.SuperType -> return null
        }
        if (member !is KtNamedFunction && member !is KtProperty && member !is KtParameter) return null
        return runReadAction {
            if (member.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
                myMemberInfoModel.isFixedAbstract(memberInfo)?.let { return@runReadAction it }
            }
            if (myMemberInfoModel.isAbstractEnabled(memberInfo)) return@runReadAction memberInfo.isToAbstract
            myMemberInfoModel.isAbstractWhenDisabled(memberInfo)
        }
    }

    override fun isAbstractColumnEditable(rowIndex: Int): Boolean {
        val memberInfo: I = myMemberInfos[rowIndex]

        if (memberInfo.isStatic) return false

        val member = when (memberInfo) {
            is KotlinMemberInfo.Declaration -> memberInfo.declaration
            is KotlinMemberInfo.SuperType -> return false
        }
        if (member !is KtNamedFunction && member !is KtProperty && member !is KtParameter) return false

        return runReadAction {
            if (member.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
                myMemberInfoModel.isFixedAbstract(memberInfo)?.let { return@runReadAction false }
            }

            memberInfo.isChecked && myMemberInfoModel.isAbstractEnabled(memberInfo)
        }
    }

    override fun setVisibilityIcon(memberInfo: I, icon: RowIcon) {
        val modifierList = when (memberInfo) {
            is KotlinMemberInfo.Declaration -> memberInfo.declaration.modifierList
            is KotlinMemberInfo.SuperType -> null
        }
        icon.setIcon(KotlinIconProvider.getVisibilityIcon(modifierList), 1)
    }

    override fun getOverrideIcon(memberInfo: I): Icon? {
        val defaultIcon = EMPTY_OVERRIDE_ICON

        val member = (memberInfo as? KotlinMemberInfo.Declaration)?.declaration ?: return defaultIcon
        if (member !is KtNamedFunction && member !is KtProperty && member !is KtParameter) return defaultIcon

        return when (memberInfo.overrides) {
            true -> AllIcons.General.OverridingMethod
            false -> AllIcons.General.ImplementingMethod
            else -> defaultIcon
        }
    }
}
