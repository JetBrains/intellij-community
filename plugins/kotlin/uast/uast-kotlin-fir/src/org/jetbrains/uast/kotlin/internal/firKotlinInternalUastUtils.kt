// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KtClassErrorType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeMappingMode
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.getKtModule
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.FirKotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.TypeOwnerKind
import org.jetbrains.uast.kotlin.getContainingLightClass
import org.jetbrains.uast.kotlin.lz
import org.jetbrains.uast.kotlin.psi.UastFakeLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeLightPrimaryConstructor

val firKotlinUastPlugin: FirKotlinUastLanguagePlugin by lz {
    UastLanguagePlugin.getInstances().single { it.language == KotlinLanguage.INSTANCE } as FirKotlinUastLanguagePlugin?
        ?: FirKotlinUastLanguagePlugin()
}

internal fun KtAnalysisSession.containingKtClass(
    ktConstructorSymbol: KtConstructorSymbol,
): KtClass? {
    return when (val psi = ktConstructorSymbol.psi) {
        is KtClass -> psi
        is KtConstructor<*> -> psi.containingClass()
        else -> null
    }
}

internal fun KtAnalysisSession.toPsiClass(
    ktType: KtType,
    source: UElement?,
    context: KtElement,
    typeOwnerKind: TypeOwnerKind
): PsiClass? {
    (context as? KtClass)?.toLightClass()?.let { return it }
    return PsiTypesUtil.getPsiClass(toPsiType(ktType, source, context, typeOwnerKind, boxed = true))
}

internal fun KtAnalysisSession.toPsiMethod(functionSymbol: KtFunctionLikeSymbol): PsiMethod? {
    return when (val psi = functionSymbol.psi) {
        null -> null
        is PsiMethod -> psi
        is KtClassOrObject -> {
            psi.primaryConstructor?.getRepresentativeLightMethod()?.let { return it }
            val lc = psi.toLightClass() ?: return null
            lc.constructors.firstOrNull()?.let { return it }
            if (psi.isLocal) UastFakeLightPrimaryConstructor(psi, lc) else null
        }
        is KtFunction -> {
            // For JVM-invisible methods, such as @JvmSynthetic, LC conversion returns nothing, so fake it
            fun handleLocalOrSynthetic(source: KtFunction): PsiMethod? {
                val ktModule = source.getKtModule()
                if (ktModule !is KtSourceModule) return null
                return getContainingLightClass(source)?.let { UastFakeLightMethod(source, it) }
            }

            if (psi.isLocal)
                handleLocalOrSynthetic(psi)
            else
                psi.getRepresentativeLightMethod() ?: handleLocalOrSynthetic(psi)
        }
        else -> psi.getRepresentativeLightMethod()
    }
}

internal fun KtAnalysisSession.toPsiType(
    ktType: KtType,
    source: UElement?,
    context: KtElement,
    typeOwnerKind: TypeOwnerKind,
    boxed: Boolean = false
): PsiType =
    toPsiType(
        ktType,
        source?.getParentOfType<UDeclaration>(false)?.javaPsi as? PsiModifierListOwner,
        context,
        typeOwnerKind,
        boxed
    )

internal fun KtAnalysisSession.toPsiType(
    ktType: KtType,
    containingLightDeclaration: PsiModifierListOwner?,
    context: KtElement,
    typeOwnerKind: TypeOwnerKind,
    boxed: Boolean = false
): PsiType {
    if (ktType is KtNonErrorClassType && ktType.typeArguments.isEmpty()) {
        fun PsiPrimitiveType.orBoxed() = if (boxed) getBoxedType(context) else this
        val psiType = when (ktType.classId) {
            StandardClassIds.Int -> PsiType.INT.orBoxed()
            StandardClassIds.Long -> PsiType.LONG.orBoxed()
            StandardClassIds.Short -> PsiType.SHORT.orBoxed()
            StandardClassIds.Boolean -> PsiType.BOOLEAN.orBoxed()
            StandardClassIds.Byte -> PsiType.BYTE.orBoxed()
            StandardClassIds.Char -> PsiType.CHAR.orBoxed()
            StandardClassIds.Double -> PsiType.DOUBLE.orBoxed()
            StandardClassIds.Float -> PsiType.FLOAT.orBoxed()
            StandardClassIds.Unit -> {
                if (typeOwnerKind == TypeOwnerKind.DECLARATION && context is KtNamedFunction)
                    PsiType.VOID.orBoxed()
                else null
            }
            StandardClassIds.String -> PsiType.getJavaLangString(context.manager, context.resolveScope)
            else -> null
        }
        if (psiType != null) return psiType
    }
    val psiTypeParent: PsiElement = containingLightDeclaration ?: context
    return ktType.asPsiType(
        psiTypeParent,
        KtTypeMappingMode.DEFAULT_UAST,
        isAnnotationMethod = false
    ) ?: UastErrorType
}

internal fun KtAnalysisSession.nullability(ktType: KtType?): TypeNullability? {
    if (ktType == null) return null
    if (ktType is KtClassErrorType) return null
    return if (ktType.canBeNull) TypeNullability.NULLABLE else TypeNullability.NOT_NULL
}
