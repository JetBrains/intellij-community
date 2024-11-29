// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.psi.*
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildSubstitutor
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaErrorTypeRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.fir.StandardTypes
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

data class KotlinTypeInfo(var text: String?, val context: KtElement) {
    @OptIn(KaExperimentalApi::class)
    constructor(ktType: KaType, context: KtElement): this(analyze(context) { ktType.render(errorIgnoringRenderer, Variance.INVARIANT) }, context)
}

@KaExperimentalApi
private val errorIgnoringRenderer: KaTypeRenderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
    errorTypeRenderer = object : KaErrorTypeRenderer {
        override fun renderType(
            analysisSession: KaSession,
            type: KaErrorType,
            typeRenderer: KaTypeRenderer,
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

                        return createType(targetType?.asKaType(inheritedCallable)?.render(position = variance) ?: typeText)
                    }

                    val ktSubstitutor = createSubstitutor(inheritedCallable, baseFunction)
                    val ktType = createTypeCodeFragment(typeText.ifEmpty { StandardClassIds.Any.asFqNameString() }, baseFunction).getContentElement()?.type
                    if (ktType != null) {
                        val type = ktSubstitutor?.substitute(ktType) ?: ktType
                        val substitutedType = type.render(position = variance)
                        if (isReceiver && type is KaDefinitelyNotNullType) {
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
    val inheritor = inheritorCallable.containingDeclaration
    val base = baseCallable.containingDeclaration
    return if (inheritor is KaClassSymbol && base is KaClassSymbol) {
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