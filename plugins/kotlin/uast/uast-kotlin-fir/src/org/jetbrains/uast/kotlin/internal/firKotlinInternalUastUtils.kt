// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCall
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.types.KtClassErrorType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.FirKotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.TypeOwnerKind
import org.jetbrains.uast.kotlin.lz
import java.lang.IllegalStateException

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

internal fun KtAnalysisSession.toPsiMethod(ktCall: KtCall): PsiMethod? {
    if (ktCall.isErrorCall) return null
    val psi = ktCall.targetFunction.candidates.singleOrNull()?.psi ?: return null
    try {
        return psi.getRepresentativeLightMethod()
    } catch (e: IllegalStateException) {
        // TODO: Creating FirModuleResolveState is not yet supported for LibrarySourceInfo(libraryName=myLibrary)
        //  this happens while destructuring a variable via Pair casting (testDestructuringDeclaration).
        return null
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
    return ktType.asPsiType(psiTypeParent, TypeMappingMode.DEFAULT_UAST) ?: UastErrorType
}

internal fun KtAnalysisSession.nullability(ktType: KtType?): TypeNullability? {
    if (ktType == null) return null
    if (ktType is KtClassErrorType) return null
    return if (ktType.canBeNull) TypeNullability.NULLABLE else TypeNullability.NOT_NULL
}
