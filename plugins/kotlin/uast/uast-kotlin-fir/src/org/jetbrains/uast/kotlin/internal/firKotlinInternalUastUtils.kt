// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.lifetime.KtAlwaysAccessibleLifetimeTokenFactory
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtClassErrorType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeMappingMode
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.getKtModule
import org.jetbrains.kotlin.analysis.providers.DecompiledPsiDeclarationProvider.findPsi
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.type.MapPsiToAsmDesc
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.*
import org.jetbrains.uast.kotlin.psi.UastFakeDeserializedLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeLightPrimaryConstructor

val firKotlinUastPlugin: FirKotlinUastLanguagePlugin by lz {
    UastLanguagePlugin.getInstances().single { it.language == KotlinLanguage.INSTANCE } as FirKotlinUastLanguagePlugin?
        ?: FirKotlinUastLanguagePlugin()
}

internal inline fun <R> analyzeForUast(
    useSiteKtElement: KtElement,
    action: KtAnalysisSession.() -> R
): R =
    analyze(useSiteKtElement, KtAlwaysAccessibleLifetimeTokenFactory, action)

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
    typeOwnerKind: TypeOwnerKind,
    boxed: Boolean = true,
): PsiClass? {
    (context as? KtClass)?.toLightClass()?.let { return it }
    return PsiTypesUtil.getPsiClass(toPsiType(ktType, source, context, typeOwnerKind, boxed))
}

internal fun KtAnalysisSession.toPsiMethod(
    functionSymbol: KtFunctionLikeSymbol,
    context: KtElement,
): PsiMethod? {
    return when (val psi = psiForUast(functionSymbol, context.project)) {
        null -> null
        is PsiMethod -> psi
        is KtClassOrObject -> {
            // For synthetic members in enum classes, `psi` points to their containing enum class.
            if (psi is KtClass && psi.isEnum()) {
                val lc = psi.toLightClass() ?: return null
                lc.methods.find { it.name == (functionSymbol as? KtFunctionSymbol)?.name?.identifier }?.let { return it }
            }

            // Default primary constructor
            psi.primaryConstructor?.getRepresentativeLightMethod()?.let { return it }
            val lc = psi.toLightClass() ?: return null
            lc.constructors.firstOrNull()?.let { return it }
            if (psi.isLocal) UastFakeLightPrimaryConstructor(psi, lc) else null
        }
        is KtFunction -> {
            // For JVM-invisible methods, such as @JvmSynthetic, LC conversion returns nothing, so fake it
            fun handleLocalOrSynthetic(source: KtFunction): PsiMethod? {
                val ktModule = source.getKtModule(context.project)
                if (ktModule !is KtSourceModule) return null
                return getContainingLightClass(source)?.let { UastFakeLightMethod(source, it) }
            }

            if (psi.isLocal) {
                handleLocalOrSynthetic(psi)
            } else {
                psi.getRepresentativeLightMethod()
                    ?: handleLocalOrSynthetic(psi)
                    ?: // Deserialized member function
                        psi.containingClass()?.getClassId()?.let { classId ->
                            toPsiClass(
                                buildClassType(classId),
                                source = null,
                                context,
                                TypeOwnerKind.DECLARATION,
                                boxed = false
                            )?.let {
                                it.methods.firstOrNull { method ->
                                    method.name == psi.name && method.desc == desc(functionSymbol, method, context)
                                } ?: UastFakeDeserializedLightMethod(psi, it) // fake Java-invisible methods
                            }
                        }
                    ?: // Deserialized top-level function
                        psi.containingKtFile.findFacadeClass()?.let {
                            it.methods.firstOrNull { method ->
                                method.name == psi.name && method.desc == desc(functionSymbol, method, context)
                            } ?: UastFakeDeserializedLightMethod(psi, it) // fake Java-invisible methods
                        }
            }
        }
        else -> psi.getRepresentativeLightMethod()
    }
}

private fun KtAnalysisSession.desc(
    functionSymbol: KtFunctionLikeSymbol,
    containingLightDeclaration: PsiModifierListOwner,
    context: KtElement
): String  = buildString {
    functionSymbol.valueParameters.joinTo(this, separator = "", prefix = "(", postfix = ")") {
        MapPsiToAsmDesc.typeDesc(
            toPsiType(
                it.returnType,
                containingLightDeclaration,
                context,
                TypeOwnerKind.DECLARATION,
                ktTypeMappingMode = KtTypeMappingMode.VALUE_PARAMETER
            )
        )
    }
    append(
        MapPsiToAsmDesc.typeDesc(
            toPsiType(
                functionSymbol.returnType,
                containingLightDeclaration,
                context,
                TypeOwnerKind.DECLARATION,
                ktTypeMappingMode = KtTypeMappingMode.RETURN_TYPE
            )
        )
    )
}

internal fun KtAnalysisSession.toPsiType(
    ktType: KtType,
    source: UElement?,
    context: KtElement,
    typeOwnerKind: TypeOwnerKind,
    boxed: Boolean = false,
    ktTypeMappingMode: KtTypeMappingMode = KtTypeMappingMode.DEFAULT_UAST,
): PsiType =
    toPsiType(
        ktType,
        source?.getParentOfType<UDeclaration>(false)?.javaPsi as? PsiModifierListOwner,
        context,
        typeOwnerKind,
        boxed,
        ktTypeMappingMode
    )

internal fun KtAnalysisSession.toPsiType(
    ktType: KtType,
    containingLightDeclaration: PsiModifierListOwner?,
    context: KtElement,
    typeOwnerKind: TypeOwnerKind,
    boxed: Boolean = false,
    ktTypeMappingMode: KtTypeMappingMode = KtTypeMappingMode.DEFAULT_UAST,
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
            StandardClassIds.Unit -> convertUnitToVoidIfNeeded(context, typeOwnerKind, boxed)
            StandardClassIds.String -> PsiType.getJavaLangString(context.manager, context.resolveScope)
            else -> null
        }
        if (psiType != null) return psiType
    }
    val psiTypeParent: PsiElement = containingLightDeclaration ?: context
    return ktType.asPsiType(
        psiTypeParent,
        ktTypeMappingMode,
        isAnnotationMethod = false
    ) ?: UastErrorType
}

internal fun KtAnalysisSession.isExtension(
    ktCall: KtCallableMemberCall<*, *>
): Boolean {
    return ktCall.symbol.isExtension
}

internal fun KtAnalysisSession.receiverType(
    ktCall: KtCallableMemberCall<*, *>,
    source: UElement,
    context: KtElement,
): PsiType? {
    var ktType = ktCall.partiallyAppliedSymbol.signature.receiverType
    if (ktType == null) {
        ktType =
            if (isExtension(ktCall))
                ktCall.partiallyAppliedSymbol.extensionReceiver?.type
            else
                ktCall.partiallyAppliedSymbol.dispatchReceiver?.type
    }
    if (ktType == null || ktType is KtClassErrorType) return null
    return toPsiType(ktType, source, context, context.typeOwnerKind, boxed = true)
}

internal fun KtAnalysisSession.nullability(ktType: KtType?): TypeNullability? {
    if (ktType == null) return null
    if (ktType is KtClassErrorType) return null
    return if (ktType.canBeNull) TypeNullability.NULLABLE else TypeNullability.NOT_NULL
}

internal fun KtAnalysisSession.nullability(ktCallableDeclaration: KtCallableDeclaration): TypeNullability? {
    val ktType = (ktCallableDeclaration.getSymbol() as? KtCallableSymbol)?.returnType
    return nullability(ktType)
}

internal fun KtAnalysisSession.nullability(ktDeclaration: KtDeclaration): TypeNullability? {
    return nullability(ktDeclaration.getReturnKtType())
}

internal fun KtAnalysisSession.nullability(ktExpression: KtExpression): TypeNullability? {
    return nullability(ktExpression.getKtType())
}

/**
 * Finds Java stub-based [PsiElement] for symbols that refer to declarations in [KtLibraryModule].
 */
internal fun KtAnalysisSession.psiForUast(ktSymbol: KtSymbol, project: Project): PsiElement? {
    return when (ktSymbol.origin) {
        KtSymbolOrigin.LIBRARY -> {
            findPsi(ktSymbol, project) ?: ktSymbol.psi
        }
        KtSymbolOrigin.INTERSECTION_OVERRIDE,
        KtSymbolOrigin.SUBSTITUTION_OVERRIDE -> {
            (ktSymbol as? KtCallableSymbol)?.originalOverriddenSymbol?.let {
                psiForUast(it, project)
            }
        }
        else -> ktSymbol.psi
    }
}
