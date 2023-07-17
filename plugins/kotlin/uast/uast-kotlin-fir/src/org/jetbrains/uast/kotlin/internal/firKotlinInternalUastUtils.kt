// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.getModule
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.providers.DecompiledPsiDeclarationProvider.findPsi
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.type.MapPsiToAsmDesc
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.*
import org.jetbrains.uast.kotlin.psi.UastFakeDeserializedLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightPrimaryConstructor

val firKotlinUastPlugin: FirKotlinUastLanguagePlugin by lz {
    UastLanguagePlugin.getInstances().single { it.language == KotlinLanguage.INSTANCE } as FirKotlinUastLanguagePlugin?
        ?: FirKotlinUastLanguagePlugin()
}

@OptIn(KtAllowAnalysisOnEdt::class)
internal inline fun <R> analyzeForUast(
    useSiteKtElement: KtElement,
    action: KtAnalysisSession.() -> R
): R = allowAnalysisOnEdt {
    analyze(useSiteKtElement, action)
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
    typeOwnerKind: TypeOwnerKind,
    isBoxed: Boolean = true,
): PsiClass? {
    (context as? KtClass)?.toLightClass()?.let { return it }
    return PsiTypesUtil.getPsiClass(
        toPsiType(
            ktType,
            source,
            context,
            PsiTypeConversionConfiguration(typeOwnerKind, isBoxed = isBoxed)
        )
    )
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
            if (psi.isLocal) UastFakeSourceLightPrimaryConstructor(psi, lc) else null
        }
        is KtFunction -> {
            // For JVM-invisible methods, such as @JvmSynthetic, LC conversion returns nothing, so fake it
            fun handleLocalOrSynthetic(source: KtFunction): PsiMethod? {
                val ktModule = getModule(source)
                if (ktModule !is KtSourceModule) return null
                return getContainingLightClass(source)?.let { UastFakeSourceLightMethod(source, it) }
            }

            when {
                psi.isLocal ->
                    handleLocalOrSynthetic(psi)
                functionSymbol.unwrapFakeOverrides.origin == KtSymbolOrigin.LIBRARY ->
                    // PSI to regular libraries should be handled by [DecompiledPsiDeclarationProvider]
                    // That is, this one is a deserialized declaration.
                    toPsiMethodForDeserialized(functionSymbol, context, psi)
                else ->
                    psi.getRepresentativeLightMethod()
                        ?: handleLocalOrSynthetic(psi)
            }
        }
        else -> psi.getRepresentativeLightMethod()
    }
}

private fun KtAnalysisSession.toPsiMethodForDeserialized(
    functionSymbol: KtFunctionLikeSymbol,
    context: KtElement,
    psi: KtFunction,
): PsiMethod? {

    // NB: no fake generation for member functions, as deserialized source PSI for built-ins can trigger FIR build/resolution
    fun PsiClass.lookup(fake: Boolean): PsiMethod? {
        val candidates =
            if (functionSymbol is KtConstructorSymbol)
                constructors.filter { it.parameterList.parameters.size == functionSymbol.valueParameters.size }
            else
                methods.filter { it.name == psi.name }
        return when (candidates.size) {
            0 -> if (fake) UastFakeDeserializedLightMethod(psi, this) else null
            1 -> candidates.single()
            else -> {
                candidates.firstOrNull { it.desc == desc(functionSymbol, it, context) } ?: candidates.first()
            }
        }
    }

    // Deserialized member function
    return psi.containingClass()?.getClassId()?.let { classId ->
        toPsiClass(
            buildClassType(classId),
            source = null,
            context,
            TypeOwnerKind.DECLARATION,
            isBoxed = false
        )?.lookup(fake = false)
    } ?:
    // Deserialized top-level function
    psi.containingKtFile.findFacadeClass()?.lookup(fake = true)
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
                PsiTypeConversionConfiguration(
                    TypeOwnerKind.DECLARATION,
                    typeMappingMode = KtTypeMappingMode.VALUE_PARAMETER,
                )
            )
        )
    }
    append(
        MapPsiToAsmDesc.typeDesc(
            toPsiType(
                functionSymbol.returnType,
                containingLightDeclaration,
                context,
                PsiTypeConversionConfiguration(
                    TypeOwnerKind.DECLARATION,
                    typeMappingMode = KtTypeMappingMode.RETURN_TYPE,
                )
            )
        )
    )
}

internal fun KtAnalysisSession.toPsiType(
    ktType: KtType,
    source: UElement?,
    context: KtElement,
    config: PsiTypeConversionConfiguration,
): PsiType =
    toPsiType(
        ktType,
        source?.getParentOfType<UDeclaration>(false)?.javaPsi as? PsiModifierListOwner,
        context,
        config
    )

internal fun KtAnalysisSession.toPsiType(
    ktType: KtType,
    containingLightDeclaration: PsiModifierListOwner?,
    context: KtElement,
    config: PsiTypeConversionConfiguration,
): PsiType {
    if (ktType is KtNonErrorClassType && ktType.ownTypeArguments.isEmpty()) {
        fun PsiPrimitiveType.orBoxed() = if (config.isBoxed) getBoxedType(context) else this
        val psiType = when (ktType.classId) {
            StandardClassIds.Int -> PsiTypes.intType().orBoxed()
            StandardClassIds.Long -> PsiTypes.longType().orBoxed()
            StandardClassIds.Short -> PsiTypes.shortType().orBoxed()
            StandardClassIds.Boolean -> PsiTypes.booleanType().orBoxed()
            StandardClassIds.Byte -> PsiTypes.byteType().orBoxed()
            StandardClassIds.Char -> PsiTypes.charType().orBoxed()
            StandardClassIds.Double -> PsiTypes.doubleType().orBoxed()
            StandardClassIds.Float -> PsiTypes.floatType().orBoxed()
            StandardClassIds.Unit -> convertUnitToVoidIfNeeded(context, config.typeOwnerKind, config.isBoxed)
            StandardClassIds.String -> PsiType.getJavaLangString(context.manager, context.resolveScope)
            else -> null
        }
        if (psiType != null) return psiType
    }
    val psiTypeParent: PsiElement = containingLightDeclaration ?: context
    return ktType.asPsiType(
        psiTypeParent,
        allowErrorTypes = false,
        config.typeMappingMode,
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
    if (ktType == null || ktType is KtErrorType) return null
    return toPsiType(
        ktType,
        source,
        context,
        PsiTypeConversionConfiguration.create(
            context,
            isBoxed = true,
        )
    )
}

internal fun KtAnalysisSession.isInheritedGenericType(ktType: KtType?): Boolean {
    if (ktType == null) return false
    return ktType is KtTypeParameterType &&
        // explicitly nullable, e.g., T?
        !ktType.isMarkedNullable &&
        // non-null upper bound, e.g., T : Any
        nullability(ktType) != KtTypeNullability.NON_NULLABLE
}

internal fun KtAnalysisSession.nullability(ktType: KtType?): KtTypeNullability? {
    if (ktType == null) return null
    if (ktType is KtErrorType) return null
    return if (ktType.canBeNull)
        KtTypeNullability.NULLABLE
    else
        KtTypeNullability.NON_NULLABLE
}

internal fun KtAnalysisSession.getKtType(ktCallableDeclaration: KtCallableDeclaration): KtType? {
    return (ktCallableDeclaration.getSymbol() as? KtCallableSymbol)?.returnType
}

/**
 * Finds Java stub-based [PsiElement] for symbols that refer to declarations in [KtLibraryModule].
 */
internal tailrec fun KtAnalysisSession.psiForUast(symbol: KtSymbol, project: Project): PsiElement? {
    if (symbol.origin == KtSymbolOrigin.LIBRARY) {
        return findPsi(symbol, project) ?: symbol.psi
    }

    if (symbol is KtCallableSymbol) {
        if (symbol.origin == KtSymbolOrigin.INTERSECTION_OVERRIDE || symbol.origin == KtSymbolOrigin.SUBSTITUTION_OVERRIDE) {
            val originalSymbol = symbol.unwrapFakeOverrides
            if (originalSymbol !== symbol) {
                return psiForUast(originalSymbol, project)
            }
        }
    }

    return symbol.psi
}
