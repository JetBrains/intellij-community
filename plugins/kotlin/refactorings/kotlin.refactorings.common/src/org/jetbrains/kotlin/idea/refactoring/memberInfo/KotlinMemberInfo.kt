// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.psi.*
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.classMembers.MemberInfoBase
import com.intellij.refactoring.util.classMembers.MemberInfo
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class KotlinMemberInfo @JvmOverloads constructor(
    member: KtNamedDeclaration,
    val isSuperClass: Boolean = false,
    val isCompanionMember: Boolean = false
) : MemberInfoBase<KtNamedDeclaration>(member) {
    init {
        isStatic = member.parent is KtFile
        if ((member is KtClass || member is KtPsiClassWrapper) && isSuperClass) {
            if (member.isInterfaceClass()) {
                displayName = RefactoringBundle.message("member.info.implements.0", member.name)
                overrides = false
            } else {
                displayName = RefactoringBundle.message("member.info.extends.0", member.name)
                overrides = true
            }
        } else {
            displayName = KotlinMemberInfoSupport.getInstance().renderMemberInfo(member)
            if (member.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
                displayName = KotlinBundle.message("member.info.abstract.0", displayName)
            }
            if (isCompanionMember) {
                displayName = KotlinBundle.message("member.info.companion.0", displayName)
            }
            overrides = KotlinMemberInfoSupport.getInstance().getOverrides(member)
        }
    }

    private fun PsiNamedElement.isInterfaceClass(): Boolean = when (this) {
        is KtClass -> isInterface()
        is PsiClass -> isInterface
        is KtPsiClassWrapper -> psiClass.isInterface
        else -> false
    }
}

fun lightElementForMemberInfo(declaration: KtNamedDeclaration?): PsiMember? {
    return when (declaration) {
        is KtNamedFunction -> declaration.getRepresentativeLightMethod()
        is KtProperty, is KtParameter -> declaration.toLightElements().let {
            it.firstIsInstanceOrNull<PsiMethod>() ?: it.firstIsInstanceOrNull<PsiField>()
        }
        is KtClassOrObject -> declaration.toLightClass()
        is KtPsiClassWrapper -> declaration.psiClass
        else -> null
    }
}

fun MemberInfoBase<out KtNamedDeclaration>.toJavaMemberInfo(): MemberInfo? {
    val declaration = member
    val psiMember: PsiMember? = lightElementForMemberInfo(declaration)
    val info = MemberInfo(psiMember ?: return null, psiMember is PsiClass && overrides != null, null)
    info.isToAbstract = isToAbstract
    info.isChecked = isChecked
    return info
}

@Suppress("unused") // used in third-party plugins
fun MemberInfo.toKotlinMemberInfo(): KotlinMemberInfo? {
    val declaration = member.unwrapped as? KtNamedDeclaration ?: return null
    return KotlinMemberInfo(declaration, declaration is KtClass && overrides != null).apply {
        this.isToAbstract = this@toKotlinMemberInfo.isToAbstract
    }
}