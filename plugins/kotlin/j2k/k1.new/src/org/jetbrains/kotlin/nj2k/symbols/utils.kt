// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.symbols

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.findDeepestSuperMethodsNoWrapping
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.nj2k.isObjectOrCompanionObject
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


internal val JKSymbol.isUnresolved
    get() = this is JKUnresolvedSymbol

internal fun JKSymbol.getDisplayFqName(): String {
    fun JKSymbol.isDisplayable() = this is JKClassSymbol || this is JKPackageSymbol
    if (this !is JKUniverseSymbol<*>) return fqName
    return generateSequence(declaredIn?.takeIf { it.isDisplayable() }) { symbol ->
        symbol.declaredIn?.takeIf { it.isDisplayable() }
    }.fold(name) { acc, symbol -> "${symbol.name}.$acc" }
}

internal fun JKSymbol.deepestFqName(): String {
    fun Any.deepestFqNameForTarget(): String? =
        when (this) {
            is PsiMethod -> (findDeepestSuperMethods().firstOrNull() ?: this).kotlinFqName?.asString()
            is KtNamedFunction -> findDeepestSuperMethodsNoWrapping(this).firstOrNull()?.kotlinFqName?.asString()
            is JKMethod -> psi<PsiElement>()?.deepestFqNameForTarget()
            else -> null
        }
    return target.deepestFqNameForTarget() ?: fqName
}

internal val JKSymbol.containingClass
    get() = declaredIn as? JKClassSymbol

internal val JKSymbol.isStaticMember
    get() = when (val target = target) {
        is PsiModifierListOwner -> target.hasModifier(JvmModifier.STATIC)
        is KtElement -> target.getStrictParentOfType<KtClassOrObject>()
            ?.safeAs<KtObjectDeclaration>()
            ?.isCompanion() == true

        is JKTreeElement ->
            target.safeAs<JKOtherModifiersOwner>()?.hasOtherModifier(OtherModifier.STATIC) == true
                    || target.parent.safeAs<JKClassBody>()?.parent.safeAs<JKClass>()?.isObjectOrCompanionObject == true

        else -> false
    }

internal val JKSymbol.isEnumConstant
    get() = when (target) {
        is JKEnumConstant -> true
        is PsiEnumConstant -> true
        is KtEnumEntry -> true
        else -> false
    }

internal val JKSymbol.isUnnamedCompanion
    get() = when (val target = target) {
        is JKClass -> target.classKind == JKClass.ClassKind.COMPANION
        is KtObjectDeclaration -> target.isCompanion() && target.name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT.toString()
        else -> false
    }

