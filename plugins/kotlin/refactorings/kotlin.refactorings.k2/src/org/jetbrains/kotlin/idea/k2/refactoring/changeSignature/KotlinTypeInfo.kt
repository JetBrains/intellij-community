// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.psi.*
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.kotlin.analysis.api.KaAnalysisNonPublicApi
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildSubstitutor
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.KtTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtTypeErrorTypeRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KtDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeErrorType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

data class KotlinTypeInfo(var text: String?, val context: KtElement) {
    @OptIn(KaExperimentalApi::class)
    constructor(ktType: KtType, context: KtElement): this(analyze(context) { ktType.render(errorIgnoringRenderer, Variance.INVARIANT) }, context)
}

@KaExperimentalApi
@OptIn(KaAnalysisNonPublicApi::class)
private val errorIgnoringRenderer: KtTypeRenderer = KtTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
    errorTypeRenderer = object : KtTypeErrorTypeRenderer {
        override fun renderType(
            analysisSession: KaSession,
            type: KtTypeErrorType,
            typeRenderer: KtTypeRenderer,
            printer: PrettyPrinter
        ) {
            type.presentableText?.let {
                printer.append(it)
            }
        }
    }
}

@OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class, KaExperimentalApi::class)
internal fun KtPsiFactory.createType(
    typeText: String,
    inheritedCallable: KtDeclaration?,
    baseFunction: PsiElement,
    variance: Variance,
    isReceiver: Boolean = false
): KtTypeReference {
    if (inheritedCallable != null) {
        allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                analyze(inheritedCallable) {
                    if (baseFunction is PsiMethod) {
                        val substitutor = if (inheritedCallable is KtFunctionLiteral) {
                            val containingClass = baseFunction.containingClass
                            val functionalType = inheritedCallable.expectedType?.asPsiType(inheritedCallable, true)
                            if (containingClass != null && functionalType is PsiClassType)
                                TypeConversionUtil.getSuperClassSubstitutor(containingClass, functionalType) else null
                        } else {
                            MethodSignatureUtil.getSuperMethodSignatureSubstitutor(
                                inheritedCallable.toLightMethods()[0].getSignature(PsiSubstitutor.EMPTY),
                                baseFunction.getSignature(PsiSubstitutor.EMPTY)
                            )
                        }
                        val targetType =
                            substitutor?.substitute(JavaPsiFacade.getElementFactory(baseFunction.project).createTypeFromText(typeText, baseFunction))

                        return createType(targetType?.asKtType(inheritedCallable)?.render(position = variance) ?: typeText)
                    }

                    val ktSubstitutor = createSubstitutor(inheritedCallable, baseFunction)
                    val ktType = createTypeCodeFragment(typeText, baseFunction).getContentElement()?.getKtType()
                    if (ktType != null) {
                        val type = ktSubstitutor?.substitute(ktType) ?: ktType
                        val substitutedType = type.render(position = variance)
                        if (isReceiver && type is KtDefinitelyNotNullType) {
                            return createType("($substitutedType)")
                        }
                        return createType(substitutedType)
                    }
                }
            }
        }
    }
    return createType(typeText)
}

context(KaSession)
@KaExperimentalApi
private fun createSubstitutor(inheritorDeclaration: KtDeclaration, baseFunction: PsiElement): KaSubstitutor? {
    val inheritorCallable = inheritorDeclaration.symbol
    val baseCallable = (baseFunction as? KtCallableDeclaration)?.symbol
        ?: (baseFunction as? PsiMember)?.callableSymbol ?: return null
    val inheritor = inheritorCallable.containingSymbol
    val base = baseCallable.containingSymbol
    return if (inheritor is KaClassOrObjectSymbol && base is KaClassOrObjectSymbol) {
        createInheritanceTypeSubstitutor(inheritor, base)?.let { iSubstitutor ->
            buildSubstitutor {
                base.typeParameters.forEach {
                    substitution(it, iSubstitutor.substitute(buildTypeParameterType(it)))
                }
                baseCallable.typeParameters.zip(inheritorCallable.typeParameters).forEach {
                    substitution(it.first, buildTypeParameterType(it.second))
                }
            }
        }
    } else null
}