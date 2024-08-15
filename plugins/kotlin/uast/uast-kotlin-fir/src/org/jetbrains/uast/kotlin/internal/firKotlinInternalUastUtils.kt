// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.analysis.api.*
import org.jetbrains.kotlin.analysis.api.annotations.*
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.asJava.*
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.PROPERTY_GETTER
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.PROPERTY_SETTER
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.*
import org.jetbrains.uast.kotlin.psi.UastFakeDeserializedSourceLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeDeserializedSymbolLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightPrimaryConstructor

val firKotlinUastPlugin: FirKotlinUastLanguagePlugin by lazyPub {
    UastLanguagePlugin.getInstances().single { it.language == KotlinLanguage.INSTANCE } as FirKotlinUastLanguagePlugin?
        ?: FirKotlinUastLanguagePlugin()
}

@OptIn(KaAllowAnalysisOnEdt::class)
internal inline fun <R> analyzeForUast(
    useSiteKtElement: KtElement,
    action: KaSession.() -> R
): R = allowAnalysisOnEdt {
    @OptIn(KaAllowAnalysisFromWriteAction::class)
    allowAnalysisFromWriteAction {
        analyze(useSiteKtElement, action)
    }
}

context(KaSession)
internal fun containingKtClass(
    ktConstructorSymbol: KaConstructorSymbol,
): KtClass? {
    return when (val psi = ktConstructorSymbol.psi) {
        is KtClass -> psi
        is KtConstructor<*> -> psi.containingClass()
        else -> null
    }
}

context(KaSession)
internal fun toPsiClass(
    ktType: KaType,
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

context(KaSession)
@OptIn(KaExperimentalApi::class)
internal fun toPsiMethod(
    functionSymbol: KaFunctionSymbol,
    context: KtElement,
    kaCallInfo: KaCallInfo? = null,
): PsiMethod? {
    // `inline` w/ `reified` type param from binary dependency,
    // which we can't find source PSI, so fake it
    if (functionSymbol.origin == KaSymbolOrigin.LIBRARY &&
        (functionSymbol as? KaNamedFunctionSymbol)?.isInline == true &&
        functionSymbol.typeParameters.any { it.isReified }
    ) {
        functionSymbol.containingJvmClassName?.let { fqName ->
            JavaPsiFacade.getInstance(context.project)
                .findClass(fqName, context.resolveScope)
                ?.let { containingClass ->
                    return UastFakeDeserializedSymbolLightMethod(
                        functionSymbol.createPointer(),
                        functionSymbol.name.identifier,
                        containingClass,
                        context,
                        kaCallInfo.typeArgumentsMappingOrEmptyMap()
                    )
                }
        }
    }
    return when (val psi = psiForUast(functionSymbol)) {
        null -> {
            // Lint/UAST CLI: try `fake` creation for a deserialized declaration
            toPsiMethodForDeserialized(functionSymbol, context, psi, kaCallInfo)
        }
        is PsiMethod -> psi
        is KtClassOrObject -> {
            // For synthetic members in enum classes, `psi` points to their containing enum class.
            if (psi is KtClass && psi.isEnum()) {
                val lc = psi.toLightClass() ?: return null
                lc.methods.find { it.name == (functionSymbol as? KaNamedFunctionSymbol)?.name?.identifier }?.let { return it }
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
                val module = getModule(source)
                if (module !is KaSourceModule) return null
                return getContainingLightClass(source)?.let { UastFakeSourceLightMethod(source, it) }
            }

            when {
                psi.isLocal ->
                    handleLocalOrSynthetic(psi)
                functionSymbol.fakeOverrideOriginal.origin == KaSymbolOrigin.LIBRARY ->
                    // PSI to regular libraries should be handled by [DecompiledPsiDeclarationProvider]
                    // That is, this one is a deserialized declaration (in Lint/UAST IDE).
                    toPsiMethodForDeserialized(functionSymbol, context, psi, kaCallInfo)
                else ->
                    psi.getRepresentativeLightMethod()
                        ?: handleLocalOrSynthetic(psi)
            }
        }
        else -> psi.getRepresentativeLightMethod()
    }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun toPsiMethodForDeserialized(
    functionSymbol: KaFunctionSymbol,
    context: KtElement,
    psi: KtFunction?,
    kaCallInfo: KaCallInfo? = null,
): PsiMethod? {

    fun equalSignatures(psiMethod: PsiMethod): Boolean {
        val methodParameters: Array<PsiParameter> = psiMethod.parameterList.parameters
        val symbolParameters: List<KaValueParameterSymbol> = functionSymbol.valueParameters
        if (methodParameters.size != symbolParameters.size) {
            return false
        }

        for (i in methodParameters.indices) {
            val symbolParameter = symbolParameters[i]
            val symbolParameterType = toPsiType(
                symbolParameter.returnType,
                psiMethod,
                context,
                PsiTypeConversionConfiguration(
                    TypeOwnerKind.DECLARATION,
                    typeMappingMode = KaTypeMappingMode.VALUE_PARAMETER,
                )
            )

            if (methodParameters[i].type != symbolParameterType) return false
        }
        val psiMethodReturnType = psiMethod.returnType ?: PsiTypes.voidType()
        val symbolReturnType = toPsiType(
            functionSymbol.returnType,
            psiMethod,
            context,
            PsiTypeConversionConfiguration(
                TypeOwnerKind.DECLARATION,
                typeMappingMode = KaTypeMappingMode.RETURN_TYPE,
            )
        )

        return psiMethodReturnType == symbolReturnType
    }

    fun PsiClass.lookup(): PsiMethod? {
        val candidates =
            if (functionSymbol is KaConstructorSymbol)
                constructors.filter { it.parameterList.parameters.size == functionSymbol.valueParameters.size }
            else {
                val jvmName = when (functionSymbol) {
                    is KaPropertyGetterSymbol -> {
                        functionSymbol.getJvmNameFromAnnotation(allowedUseSiteTargets = setOf(PROPERTY_GETTER, null))
                    }
                    is KaPropertySetterSymbol -> {
                        functionSymbol.getJvmNameFromAnnotation(allowedUseSiteTargets = setOf(PROPERTY_SETTER, null))
                    }
                    else -> {
                        functionSymbol.getJvmNameFromAnnotation()
                    }
                }
                val id = jvmName
                    ?: functionSymbol.callableId?.callableName?.identifierOrNullIfSpecial
                    ?: psi?.name
                methods.filter { it.name == id }
            }
        return when (candidates.size) {
            0 -> {
                if (psi != null) {
                    UastFakeDeserializedSourceLightMethod(psi, this@lookup)
                } else if (functionSymbol is KaNamedFunctionSymbol) {
                    UastFakeDeserializedSymbolLightMethod(
                        functionSymbol.createPointer(),
                        functionSymbol.name.identifier,
                        this@lookup,
                        context,
                        kaCallInfo.typeArgumentsMappingOrEmptyMap()
                    )
                } else null
            }
            1 -> {
                candidates.single()
            }
            else -> {
                candidates.firstOrNull { equalSignatures(it) } ?: candidates.first()
            }
        }
    }

    // Deserialized member function
    val classId = psi?.containingClass()?.getClassId()
        ?: functionSymbol.callableId?.classId
    if (classId != null) {
        toPsiClass(
            buildClassType(classId),
            source = null,
            context,
            TypeOwnerKind.DECLARATION,
            isBoxed = false,
        )?.lookup()?.let { return it }
    }
    // Deserialized top-level function
    return if (psi != null) {
        // Lint/UAST IDE: with deserialized PSI
        psi.containingKtFile.findFacadeClass()?.lookup()
    } else if (functionSymbol is KaNamedFunctionSymbol) {
        // Lint/UAST CLI: attempt to find the binary class
        //   with the facade fq name from the resolved symbol
        functionSymbol.containingJvmClassName?.let { fqName ->
            JavaPsiFacade.getInstance(context.project)
                .findClass(fqName, context.resolveScope)
                ?.lookup()
        }
    } else null
}

@OptIn(KaExperimentalApi::class)
private fun KaCallInfo?.typeArgumentsMappingOrEmptyMap(): Map<KaSymbolPointer<KaTypeParameterSymbol>, KaTypePointer<KaType>> =
    (this?.successfulCallOrNull<KaCall>() as? KaCallableMemberCall<*, *>)
        ?.typeArgumentsMapping
        ?.map { (typeParamSymbol, type) ->
            typeParamSymbol.createPointer() to type.createPointer()
        }?.toMap()
        ?: emptyMap()

/**
 * Returns a `JvmName` annotation value.
 *
 * @param allowedUseSiteTargets If non-empty, only annotations with the specified use-site targets are checked.
 */
private fun KaAnnotatedSymbol.getJvmNameFromAnnotation(allowedUseSiteTargets: Set<AnnotationUseSiteTarget?> = emptySet()): String? {
    for (annotation in annotations[JvmStandardClassIds.JVM_NAME_CLASS_ID]) {
        if (allowedUseSiteTargets.isEmpty() || annotation.useSiteTarget in allowedUseSiteTargets) {
            val firstArgumentExpression = annotation.arguments.firstOrNull()?.expression
            if (firstArgumentExpression is KaAnnotationValue.ConstantValue) {
                return firstArgumentExpression.value.value as? String
            }
            break
        }
    }

    return null
}

context(KaSession)
internal fun toPsiType(
    ktType: KaType,
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

context(KaSession)
@OptIn(KaExperimentalApi::class)
internal fun toPsiType(
    ktType: KaType,
    containingLightDeclaration: PsiModifierListOwner?,
    context: KtElement,
    config: PsiTypeConversionConfiguration,
): PsiType {
    if (ktType is KaClassType && ktType.typeArguments.isEmpty()) {
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

context(KaSession)
internal fun receiverType(
    ktCall: KaCallableMemberCall<*, *>,
    source: UElement,
    context: KtElement,
): PsiType? {
    val ktType = ktCall.partiallyAppliedSymbol.signature.receiverType
        ?: ktCall.partiallyAppliedSymbol.extensionReceiver?.type
        ?: ktCall.partiallyAppliedSymbol.dispatchReceiver?.type
    if (ktType == null ||
        ktType is KaErrorType ||
        ktType.isUnitType
    ) {
        return null
    }
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

context(KaSession)
internal val KaType.typeForValueClass: Boolean
    get() {
        val symbol = expandedSymbol as? KaNamedClassSymbol ?: return false
        return symbol.isInline
    }

context(KaSession)
internal fun isInheritedGenericType(ktType: KaType?): Boolean {
    if (ktType == null) return false
    return ktType is KaTypeParameterType &&
        // explicitly nullable, e.g., T?
        !ktType.isMarkedNullable &&
        // non-null upper bound, e.g., T : Any
        nullability(ktType) != KaTypeNullability.NON_NULLABLE
}

context(KaSession)
internal fun nullability(ktType: KaType?): KaTypeNullability? {
    if (ktType == null) return null
    if (ktType is KaErrorType) return null
    val expanded = ktType.fullyExpandedType
    return when {
        expanded.hasFlexibleNullability -> KaTypeNullability.UNKNOWN
        expanded.canBeNull -> KaTypeNullability.NULLABLE
        else -> KaTypeNullability.NON_NULLABLE
    }
}

context(KaSession)
internal fun getKtType(ktCallableDeclaration: KtCallableDeclaration): KaType? {
    return (ktCallableDeclaration.symbol as? KaCallableSymbol)?.returnType
}

/**
 * Finds Java stub-based [PsiElement] for symbols that refer to declarations in [KaLibraryModule].
 */
context(KaSession)
internal tailrec fun psiForUast(symbol: KaSymbol): PsiElement? {
    if (symbol.origin == KaSymbolOrigin.LIBRARY) {
        val psiProvider = FirKotlinUastLibraryPsiProviderService.getInstance()
        return with(psiProvider) { provide(symbol) }
    }

    if (symbol is KaCallableSymbol) {
        if (symbol.origin == KaSymbolOrigin.INTERSECTION_OVERRIDE || symbol.origin == KaSymbolOrigin.SUBSTITUTION_OVERRIDE) {
            val originalSymbol = symbol.fakeOverrideOriginal
            if (originalSymbol != symbol) {
                return psiForUast(originalSymbol)
            }
        }
    }

    return symbol.psi
}

internal fun KtElement.toPsiElementAsLightElement(
    sourcePsi: KtExpression? = null
): PsiElement? {
    if (this is KtProperty) {
        with(getAccessorLightMethods()) {
            // Weigh [PsiField]
            backingField?.let { return it }
            val readWriteAccess = sourcePsi?.readWriteAccess()
            when {
                readWriteAccess?.isWrite == true -> {
                    setter?.let { return it }
                }
                readWriteAccess?.isRead == true -> {
                    getter?.let { return it }
                }
                else -> {}
            }
        }
    }
    return toLightElements().firstOrNull()
}
