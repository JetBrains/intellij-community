// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.lightElementForMemberInfo
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

@ApiStatus.Internal
fun KtProperty.mustBeAbstractInInterface(): Boolean =
    hasInitializer() || hasDelegate() || (!hasInitializer() && !hasDelegate() && accessors.isEmpty())

@ApiStatus.Internal
fun KtNamedDeclaration.isAbstractInInterface(originalClass: KtClassOrObject): Boolean =
    originalClass is KtClass && originalClass.isInterface() && isAbstract()

@ApiStatus.Internal
fun KtNamedDeclaration.canMoveMemberToJavaClass(targetClass: PsiClass): Boolean {
    return when (this) {
        is KtProperty, is KtParameter -> {
            if (targetClass.isInterface) return false
            if (hasModifier(KtTokens.OPEN_KEYWORD) || hasModifier(KtTokens.ABSTRACT_KEYWORD)) return false
            if (this is KtProperty && (accessors.isNotEmpty() || delegateExpression != null)) return false
            true
        }
        is KtNamedFunction -> valueParameters.all { it.defaultValue == null }
        else -> false
    }
}

@ApiStatus.Internal
fun getInterfaceContainmentVerifier(getMemberInfos: () -> List<KotlinMemberInfo>): (KtNamedDeclaration) -> Boolean = result@{ member ->
    val psiMethodToCheck = lightElementForMemberInfo(member) as? PsiMethod ?: return@result false
    getMemberInfos().any {
        if (!it.isSuperClass || it.overrides != false) return@any false

        val psiSuperInterface = (it.member as? KtClass)?.toLightClass()
        psiSuperInterface?.findMethodBySignature(psiMethodToCheck, true) != null
    }
}
