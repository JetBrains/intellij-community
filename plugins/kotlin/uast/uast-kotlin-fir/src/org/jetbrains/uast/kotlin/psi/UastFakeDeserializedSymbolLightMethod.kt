// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.psi.impl.light.*
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastLazyPart
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
    private val original: KaSymbolPointer<KaNamedFunctionSymbol>,
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
            val functionSymbol = original.restoreSymbol()
            if (functionSymbol?.location == KaSymbolLocation.TOP_LEVEL ||
                functionSymbol?.isStatic == true
            ) {
                addModifier(PsiModifier.STATIC)
            }
        }
    }

    private val returnTypePart = UastLazyPart<PsiType?>()

    private val _returnType: PsiType?
        get() = returnTypePart.getOrBuild {
            analyzeForUast(context) {
                val functionSymbol = original.restoreSymbol() ?: return@analyzeForUast PsiTypes.nullType()
                val returnType = functionSymbol.returnType
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
    private fun KaSession.lookupTypeArgument(type: KaTypeParameterType): KaType? {
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
                val functionSymbol = original.restoreSymbol() ?: return@analyzeForUast false
                functionSymbol.isSuspend
            }
        }

    private val _isUnit = UastLazyPart<Boolean>()

    override fun isUnitFunction(): Boolean =
        _isUnit.getOrBuild {
            analyzeForUast(context) {
                val functionSymbol = original.restoreSymbol() ?: return@analyzeForUast false
                functionSymbol.returnType.isUnitType
            }
        }

    override fun computeNullability(): KaTypeNullability? {
        return analyzeForUast(context) {
            val functionSymbol = original.restoreSymbol() ?: return@analyzeForUast null
            functionSymbol.psi?.let { psi ->
                val hasInheritedGenericType = baseResolveProviderService.hasInheritedGenericType(psi)
                if (hasInheritedGenericType) {
                    // Inherited generic type: nullity will be determined at use-site
                    return@analyzeForUast null
                }
            }
            if (functionSymbol.isSuspend) {
                // suspend fun returns Any?, which is mapped to @Nullable java.lang.Object
                return@analyzeForUast KaTypeNullability.NULLABLE
            }
            functionSymbol.returnType.nullability
        }
    }

    override fun computeAnnotations(annotations: SmartSet<PsiAnnotation>) {
        analyzeForUast(context) {
            val functionSymbol = original.restoreSymbol() ?: return
            for (annoApp in functionSymbol.annotations) {
                annotations.add(
                    UastFakeDeserializedSymbolAnnotation(original, annoApp.classId, context)
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
                    val functionSymbol = original.restoreSymbol() ?: return extendsList
                    val typeParamSymbol = functionSymbol.typeParameters[index]
                    for (bound in typeParamSymbol.upperBounds) {
                        val psiType = bound.asPsiType(context, allowErrorTypes = true)
                        if (psiType is PsiClassType) extendsList.addReference(psiType)
                    }
                }
                return extendsList
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
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
                        val functionSymbol = original.restoreSymbol() ?: return@l
                        for ((i, typeParamSymbol) in functionSymbol.typeParameters.withIndex()) {
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
                        val functionSymbol = original.restoreSymbol() ?: return@l
                        val functionSymbolPtr = functionSymbol.createPointer()
                        functionSymbol.receiverParameter?.let { receiverParameter ->
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
                                        functionSymbolPtr.restoreSymbol()
                                            ?.receiverType
                                            ?.asPsiType(context, allowErrorTypes = true)
                                            ?: UastErrorType
                                    }
                                }
                            )
                        }

                        for (valueParamSymbol in functionSymbol.valueParameters) {
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
