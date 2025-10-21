// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ui

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.psi.PsiElement
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ExtractSuperInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperclassHandler
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.extractClassMembers
import org.jetbrains.kotlin.idea.refactoring.pullUp.getInterfaceContainmentVerifier
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

@ApiStatus.Internal
class KotlinExtractSuperclassDialog(
    originalClass: KtClassOrObject,
    targetParent: PsiElement,
    conflictChecker: (KotlinExtractSuperDialogBase) -> Boolean,
    refactoring: (ExtractSuperInfo) -> Unit
) : KotlinExtractSuperDialogBase(
    originalClass,
    targetParent,
    conflictChecker,
    false,
    KotlinExtractSuperclassHandler.REFACTORING_NAME,
    refactoring
) {
    companion object {
        private const val DESTINATION_PACKAGE_RECENT_KEY = "KotlinExtractSuperclassDialog.RECENT_KEYS"
    }

    init {
        init()
    }

    override fun createMemberInfoModel(): MemberInfoModelBase {
        return object : MemberInfoModelBase(
            originalClass,
            ActionUtil.underModalProgress(project, RefactoringBundle.message("refactoring.prepare.progress")) {
                extractClassMembers(originalClass)
            },
            getInterfaceContainmentVerifier { selectedMembers }
        ) {
            override fun isAbstractEnabled(memberInfo: KotlinMemberInfo): Boolean {
                if (!super.isAbstractEnabled(memberInfo)) return false
                val member = memberInfo.member
                return member is KtNamedFunction || member is KtProperty || member is KtParameter
            }
        }
    }

    override fun getDestinationPackageRecentKey(): String = DESTINATION_PACKAGE_RECENT_KEY

    override fun getClassNameLabelText(): String = RefactoringBundle.message("superclass.name")

    override fun getPackageNameLabelText(): String = RefactoringBundle.message("package.for.new.superclass")

    override fun getEntityName(): String = RefactoringBundle.message("ExtractSuperClass.superclass")

    override fun getTopLabelText(): String = RefactoringBundle.message("extract.superclass.from")

    override fun getDocCommentPolicySetting(): Int = KotlinCommonRefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC

    override fun setDocCommentPolicySetting(policy: Int) {
        KotlinCommonRefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC = policy
    }

    override fun getExtractedSuperNameNotSpecifiedMessage(): String = RefactoringBundle.message("no.superclass.name.specified")

    override fun getHelpId(): String = HelpID.EXTRACT_SUPERCLASS
}