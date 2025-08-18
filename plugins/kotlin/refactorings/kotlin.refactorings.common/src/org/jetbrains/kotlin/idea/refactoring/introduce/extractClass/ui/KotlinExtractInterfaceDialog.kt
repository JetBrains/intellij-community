// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ui

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ExtractSuperInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractInterfaceHandler
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.extractClassMembers
import org.jetbrains.kotlin.idea.refactoring.memberInfo.lightElementForMemberInfo
import org.jetbrains.kotlin.idea.refactoring.pullUp.getInterfaceContainmentVerifier
import org.jetbrains.kotlin.idea.refactoring.pullUp.isAbstractInInterface
import org.jetbrains.kotlin.idea.refactoring.pullUp.mustBeAbstractInInterface
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import javax.swing.JTextField

@ApiStatus.Internal
class KotlinExtractInterfaceDialog(
    originalClass: KtClassOrObject,
    targetParent: PsiElement,
    conflictChecker: (KotlinExtractSuperDialogBase) -> Boolean,
    refactoring: (ExtractSuperInfo) -> Unit
) : KotlinExtractSuperDialogBase(
    originalClass,
    targetParent,
    conflictChecker,
    true,
    KotlinExtractInterfaceHandler.REFACTORING_NAME,
    refactoring
) {
    companion object {
        private const val DESTINATION_PACKAGE_RECENT_KEY = "KotlinExtractInterfaceDialog.RECENT_KEYS"
    }

    init {
        init()
    }

    override fun createMemberInfoModel(): MemberInfoModelBase {
        val extractableMemberInfos = ActionUtil.underModalProgress(
            project,
            RefactoringBundle.message("refactoring.prepare.progress"),
        ) {
            extractClassMembers(originalClass).filterNot {
                val member = it.member
                member is KtClass && member.hasModifier(KtTokens.INNER_KEYWORD) ||
                        member is KtParameter && member.hasModifier(KtTokens.PRIVATE_KEYWORD)
            }
        }
        extractableMemberInfos.forEach { it.isToAbstract = true }
        return object : MemberInfoModelBase(
            originalClass,
            extractableMemberInfos,
            getInterfaceContainmentVerifier { selectedMembers }
        ) {
            override fun isMemberEnabled(member: KotlinMemberInfo): Boolean {
                if (!super.isMemberEnabled(member)) return false

                val declaration = member.member
                return !(declaration.hasModifier(KtTokens.INLINE_KEYWORD) ||
                        declaration.hasModifier(KtTokens.EXTERNAL_KEYWORD) ||
                        declaration.hasModifier(KtTokens.LATEINIT_KEYWORD))
            }

            override fun isAbstractEnabled(memberInfo: KotlinMemberInfo): Boolean {
                if (!super.isAbstractEnabled(memberInfo)) return false
                val member = memberInfo.member
                if (member.isAbstractInInterface(originalClass)) return false
                if (member.isConstructorDeclaredProperty()) return false
                return member is KtNamedFunction || (member is KtProperty && !member.mustBeAbstractInInterface()) || member is KtParameter
            }

            override fun isAbstractWhenDisabled(memberInfo: KotlinMemberInfo): Boolean {
                val member = memberInfo.member
                return member is KtProperty || member.isAbstractInInterface(originalClass) || member.isConstructorDeclaredProperty()
            }

            override fun checkForProblems(memberInfo: KotlinMemberInfo): Int {
                val result = super.checkForProblems(memberInfo)
                if (result != OK) return result

                if (!memberInfo.isSuperClass || memberInfo.overrides != false || memberInfo.isChecked) return OK

                val psiSuperInterface = lightElementForMemberInfo(memberInfo.member) as? PsiClass ?: return OK

                for (info in memberInfos) {
                    if (!info.isChecked || info.isToAbstract) continue
                    val member = info.member ?: continue
                    val psiMethodToCheck = lightElementForMemberInfo(member) as? PsiMethod ?: continue
                    if (psiSuperInterface.findMethodBySignature(psiMethodToCheck, true) != null) return ERROR
                }

                return OK
            }
        }
    }

    override fun getDestinationPackageRecentKey(): String = DESTINATION_PACKAGE_RECENT_KEY

    override fun getClassNameLabelText(): @Nls String = RefactoringBundle.message("interface.name.prompt")

    override fun getPackageNameLabelText(): @Nls String = RefactoringBundle.message("package.for.new.interface")

    override fun getEntityName(): @Nls String = RefactoringBundle.message("extractSuperInterface.interface")

    override fun getTopLabelText(): @Nls String = RefactoringBundle.message("extract.interface.from")

    override fun getDocCommentPolicySetting(): Int = KotlinCommonRefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC

    override fun setDocCommentPolicySetting(policy: Int) {
        KotlinCommonRefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC = policy
    }

    override fun getExtractedSuperNameNotSpecifiedMessage(): @Nls String = RefactoringBundle.message("no.interface.name.specified")

    override fun getHelpId(): String = HelpID.EXTRACT_INTERFACE

    override fun createExtractedSuperNameField(): JTextField = super.createExtractedSuperNameField().apply { text = "I${originalClass.name}" }
}