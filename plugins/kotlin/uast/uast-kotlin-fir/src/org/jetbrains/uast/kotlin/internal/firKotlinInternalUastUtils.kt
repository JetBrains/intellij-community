// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyseForUast
import org.jetbrains.kotlin.analysis.api.calls.KtCall
import org.jetbrains.kotlin.analysis.api.calls.getSingleCandidateSymbolOrNull
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.types.KtClassErrorType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeMappingMode
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.FirKotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.TypeOwnerKind
import org.jetbrains.uast.kotlin.lz
import org.jetbrains.uast.kotlin.typeOwnerKind

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

internal fun resolveToPsiClassOrEnumEntry(context: KtElement, classOrObject: KtClassOrObject): PsiElement? {
    analyseForUast(context) {
        val ktType = when (classOrObject) {
            is KtEnumEntry ->
                classOrObject.getEnumEntrySymbol().containingEnumClassIdIfNonLocal?.let { enumClassId ->
                    buildClassType(enumClassId)
                }
            else ->
                buildClassType(classOrObject.getClassOrObjectSymbol())
        } ?: return null
        val psiClass = toPsiClass(ktType, source = null, classOrObject, classOrObject.typeOwnerKind)
        return when (classOrObject) {
            is KtEnumEntry -> psiClass?.findFieldByName(classOrObject.name, false)
            else -> psiClass
        }
    }
}

internal fun KtAnalysisSession.toPsiMethod(context: KtElement, ktCall: KtCall): PsiMethod? {
    if (ktCall.isErrorCall) return null
    val psi = ktCall.targetFunction.getSingleCandidateSymbolOrNull()?.psi ?: return null
    return psi.getRepresentativeLightMethod()
        // TODO: with correct member origin, we may not need this backup.
        ?: resolveDeserialized(context, psi) as? PsiMethod
}

internal fun resolveDeserialized(context: KtElement, resolvedTargetElement: PsiElement): PsiModifierListOwner? {

    fun PsiClass.findTarget(): PsiModifierListOwner? {
        return when (resolvedTargetElement) {
            is KtClassOrObject -> {
                findInnerClassByName(resolvedTargetElement.name, false)
            }
            is KtFunction -> {
                // TODO: signature comparison to make sure and/or find the exact one
                findMethodsByName(resolvedTargetElement.name, false).firstOrNull()
                // TODO: or, with the correct member origin for compiled element, the following conversion would be natural.
                // resolvedTargetElement.toLightElements().filterIsInstance<PsiMethod>().singleOrNull()
                // TODO: or, simply `getRepresentativeLightMethod` will handle this gracefully.
                // resolvedTargetElement.getRepresentativeLightMethod()
            }
            is KtDeclaration -> {
                findFieldByName(resolvedTargetElement.name, false)
            }
            else -> null
        }
    }

    return when (val parent = resolvedTargetElement.parent) {
        is KtFile -> {
            val facade = parent.findFacadeClass() ?: return null
            facade.findTarget()
        }
        is KtClassBody -> {
            val containingClass = parent.parent as? KtClass ?: return null
            if (containingClass.containingKtFile.isCompiled) {
                val lightClass = resolveToPsiClassOrEnumEntry(context, containingClass) as? PsiClass ?: return null
                lightClass.findTarget()
            } else null
        }
        else -> null
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
