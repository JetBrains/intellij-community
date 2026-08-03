// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameterList
import com.intellij.psi.PsiTypeParameterListOwner
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.light.LightParameterListBuilder
import com.intellij.psi.impl.light.LightReferenceListBuilder
import com.intellij.psi.impl.light.LightTypeParameterBuilder
import com.intellij.psi.impl.light.LightTypeParameterListBuilder
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.asPsiType
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertyAccessorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.restoreSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeMappingMode
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.analysis.api.types.hasFlexibleNullability
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.restore
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.analysis.UNullability
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.PsiTypeConversionConfiguration
import org.jetbrains.uast.kotlin.TypeOwnerKind
import org.jetbrains.uast.kotlin.internal.analyzeForUast
import org.jetbrains.uast.kotlin.internal.toPsiType

/**
 * A fake light method from binary, which is not materialized for some reason
 * (e.g., `inline` with `reified` type parameter)
 *
 * Due to its origin, BINARY, we don't have source PSI, but at least we have a pointer to
 * Analysis API symbol if it's resolved.
 */
internal class UastFakeDeserializedSymbolLightMethod
@OptIn(KaExperimentalApi::class)
constructor(
    private val original: KaSymbolPointer<KaCallableSymbol>,
    name: String,
    containingClass: PsiClass,
    private val context: KtElement,
    private val typeArgumentMapping: Map<KaSymbolPointer<KaTypeParameterSymbol>, KaTypePointer<KaType>>,
) : UastFakeLightMethodBase(
    context.manager,
    context.language,
    name,
    LightParameterListBuilder(context.manager, context.language),
    LightModifierList(context.manager),
    containingClass
) {

    init {
        analyzeForUast(context) {
            val callableSymbol = original.restoreSymbol()

            val isTopLevel = callableSymbol?.location == KaSymbolLocation.TOP_LEVEL ||
                    (callableSymbol as? KaPropertyAccessorSymbol)?.containingDeclaration?.location == KaSymbolLocation.TOP_LEVEL

            if (isTopLevel || callableSymbol?.isStatic == true) {
                addModifier(PsiModifier.STATIC)
            }
        }
    }

    private val returnTypePart = UastLazyPart<PsiType?>()

    private val _returnType: PsiType?
        get() = returnTypePart.getOrBuild {
            analyzeForUast(context) {
                val callableSymbol = original.restoreSymbol() ?: return@analyzeForUast PsiTypes.nullType()
                val returnType = callableSymbol.returnType
                val substitutedType = if (returnType is KaTypeParameterType) {
                    lookupTypeArgument(returnType) ?: returnType
                } else
                    returnType
                toPsiType(
                    substitutedType,
                    this@UastFakeDeserializedSymbolLightMethod,
                    context,
                    PsiTypeConversionConfiguration(
                        TypeOwnerKind.DECLARATION,
                        typeMappingMode = KaTypeMappingMode.RETURN_TYPE
                    )
                )
            }
        }

    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    private fun lookupTypeArgument(type: KaTypeParameterType): KaType? {
        for (symbolPointer in typeArgumentMapping.keys) {
            val typeParameterSymbol = symbolPointer.restoreSymbol()
            if (typeParameterSymbol == type.symbol) {
                return typeArgumentMapping[symbolPointer]?.restore()
            }
        }
        return null
    }

    override fun getReturnType(): PsiType? {
        return _returnType
    }

    private val _isSuspend = UastLazyPart<Boolean>()

    override fun isSuspendFunction(): Boolean =
        _isSuspend.getOrBuild {
            analyzeForUast(context) {
                val callableSymbol = original.restoreSymbol() ?: return@analyzeForUast false
                callableSymbol.isSuspend
            }
        }

    private val _isUnit = UastLazyPart<Boolean>()

    override fun isUnitFunction(): Boolean =
        _isUnit.getOrBuild {
            analyzeForUast(context) {
                val callableSymbol = original.restoreSymbol() ?: return@analyzeForUast false
                callableSymbol.returnType.classId == KaStandardTypeClassIds.UNIT
            }
        }

    override fun computeNullability(): UNullability? {
        return analyzeForUast(context) {
            val callableSymbol = original.restoreSymbol() ?: return@analyzeForUast null
            callableSymbol.psi?.let { psi ->
                val hasInheritedGenericType = baseResolveProviderService.hasInheritedGenericType(psi)
                if (hasInheritedGenericType) {
                    // Inherited generic type: nullity will be determined at use-site
                    return@analyzeForUast null
                }
            }
            if (callableSymbol.isSuspend) {
                // suspend fun returns Any?, which is mapped to @Nullable java.lang.Object
                return@analyzeForUast UNullability.NULLABLE
            }
            val returnType = callableSymbol.returnType

            return when {
                returnType.hasFlexibleNullability -> UNullability.UNKNOWN
                returnType.isMarkedNullable -> UNullability.NULLABLE
                else -> UNullability.NOT_NULL
            }
        }
    }

    override fun computeAnnotations(annotations: SmartSet<PsiAnnotation>) {
        analyzeForUast(context) {
            val callableSymbol = original.restoreSymbol() ?: return
            for (annoApp in callableSymbol.annotations) {
                annotations.add(
                    UastFakeDeserializedSymbolAnnotation(original, annoApp.classId, context, annoApp.psi)
                )
            }
        }
    }

    private val typeParameterListPart = UastLazyPart<PsiTypeParameterList>()

    private inner class UastFakeDeserializedSymbolLightParameter(
        name: @NlsSafe String,
        owner: PsiTypeParameterListOwner,
        index: Int
    ) : LightTypeParameterBuilder(name, owner, index) {
        private val myExtendsListPart = UastLazyPart<LightReferenceListBuilder>()

        @OptIn(KaExperimentalApi::class)
        override fun getExtendsList(): LightReferenceListBuilder {
            val context = this@UastFakeDeserializedSymbolLightMethod.context
            return myExtendsListPart.getOrBuild {
                val extendsList = super.getExtendsList()
                analyzeForUast(context) {
                    val callableSymbol = original.restoreSymbol() ?: return extendsList
                    val targetSymbol = (callableSymbol as? KaPropertyAccessorSymbol)?.containingDeclaration as? KaPropertySymbol ?: callableSymbol
                    val typeParamSymbol = targetSymbol.typeParameters[index]
                    for (bound in typeParamSymbol.upperBounds) {
                        val psiType = bound.asPsiType(context, allowErrorTypes = true)
                        if (psiType is PsiClassType) extendsList.addReference(psiType)
                    }
                }
                return extendsList
            }
        }
    }

    override fun getTypeParameterList(): PsiTypeParameterList =
        typeParameterListPart.getOrBuild {
            object : LightTypeParameterListBuilder(context.manager, context.language) {
                override fun getParent(): PsiElement = this@UastFakeDeserializedSymbolLightMethod
                override fun getContainingFile(): PsiFile = parent.containingFile

                init {
                    val typeParameterList = this
                    val typeParameterOwner = this@UastFakeDeserializedSymbolLightMethod
                    val context = this@UastFakeDeserializedSymbolLightMethod.context

                    analyzeForUast(context) l@{
                        val callableSymbol = original.restoreSymbol() ?: return@l
                        val targetSymbol = (callableSymbol as? KaPropertyAccessorSymbol)?.containingDeclaration as? KaPropertySymbol ?: callableSymbol
                        for ((i, typeParamSymbol) in targetSymbol.typeParameters.withIndex()) {
                            typeParameterList.addParameter(
                                UastFakeDeserializedSymbolLightParameter(typeParamSymbol.name.identifier, typeParameterOwner, i)
                            )
                        }
                    }
                }
            }
        }

    private val parameterListPart = UastLazyPart<PsiParameterList>()

    @OptIn(KaExperimentalApi::class)
    override fun getParameterList(): PsiParameterList =
        parameterListPart.getOrBuild {
            object : LightParameterListBuilder(context.manager, context.language) {
                override fun getParent(): PsiElement = this@UastFakeDeserializedSymbolLightMethod
                override fun getContainingFile(): PsiFile = parent.containingFile

                init {
                    val parameterList = this
                    val context = this@UastFakeDeserializedSymbolLightMethod.context

                    analyzeForUast(context) l@{
                        val callableSymbol = original.restoreSymbol() ?: return@l
                        val callableSymbolPtr = callableSymbol.createPointer()
                        callableSymbol.receiverParameter?.let { receiverParameter ->
                            val receiverOrigin = receiverParameter.psi as? KtTypeReference ?: context
                            parameterList.addParameter(
                                UastKotlinPsiParameterBase(
                                    "\$this\$$name",
                                    parameterList,
                                    isVarArgs = false,
                                    ktDefaultValue = null,
                                    ktOrigin = receiverOrigin
                                ) {
                                    analyzeForUast(context) {
                                        callableSymbolPtr.restoreSymbol()
                                            ?.receiverType
                                            ?.asPsiType(context, allowErrorTypes = true)
                                            ?: UastErrorType
                                    }
                                }
                            )
                        }

                        for (valueParamSymbol in callableSymbol.valueParameters) {
                            val valueParamSymbolPtr = valueParamSymbol.createPointer()
                            parameterList.addParameter(
                                UastKotlinPsiParameterBase(
                                    valueParamSymbol.name.identifier,
                                    parameterList,
                                    isVarArgs = false,
                                    ktDefaultValue = null,
                                    ktOrigin = (valueParamSymbol.psi as? KtElement) ?: context
                                ) {
                                    analyzeForUast(context) {
                                        val restoredValueSymbol = valueParamSymbolPtr.restoreSymbol()
                                        restoredValueSymbol
                                            ?.returnType
                                            ?.asPsiType(context, allowErrorTypes = true)
                                            ?.toEllipsisTypeIfNeeded(restoredValueSymbol.isVararg)
                                            ?: UastErrorType
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
}

context(_: KaSession)
private val KaCallableSymbol.isStatic get() = when (this) {
    is KaNamedFunctionSymbol -> isStatic
    is KaPropertySymbol -> isStatic
    is KaPropertyAccessorSymbol -> (containingDeclaration as? KaPropertySymbol)?.isStatic == true
    else -> false
}

private val KaCallableSymbol.isSuspend get() = (this as? KaNamedFunctionSymbol)?.isSuspend == true

private val KaCallableSymbol.valueParameters get() = (this as? KaFunctionSymbol)?.valueParameters.orEmpty()