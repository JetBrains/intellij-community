// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.asPsiType
import org.jetbrains.kotlin.analysis.api.expressions.expectedType
import org.jetbrains.kotlin.analysis.api.javaInterop.asKaType
import org.jetbrains.kotlin.analysis.api.javaInterop.callableSymbol
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute
import org.jetbrains.kotlin.analysis.api.rendering.append
import org.jetbrains.kotlin.analysis.api.rendering.output
import org.jetbrains.kotlin.analysis.api.rendering.push
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.buildSubstitutor
import org.jetbrains.kotlin.analysis.api.types.createInheritanceTypeSubstitutor
import org.jetbrains.kotlin.analysis.api.types.restore
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.analysis.api.types.typeCreation.typeCreator
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.util.renderToStringIfPresent
import org.jetbrains.kotlin.idea.util.withQualifiedNames
import org.jetbrains.kotlin.idea.util.withTypeApproximation
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.types.Variance

data class KotlinTypeInfo(var text: String?, val context: KtElement) {
    constructor(ktType: KaType, context: KtElement): this(analyze(context) { errorIgnoringRenderer.renderToString(ktType) }, context)
}

@KaExperimentalApi
private val errorIgnoringRenderer: KaRenderer = KaRenderer.withQualifiedNames.copy {
    push(KaPiece.ErrorType) { errorType ->
        errorType.presentableText?.let { output.append(it, KaTextAttribute.Identifier) }
    }
}

@OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
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
                    val renderer = KaRenderer.withQualifiedNames.copy { withTypeApproximation(variance) }

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

                        return createType(renderer.renderToStringIfPresent(targetType?.asKaType(inheritedCallable)) ?: typeText)
                    }

                    val ktSubstitutor = createSubstitutor(inheritedCallable, baseFunction)

                    if (ktSubstitutor !is KaSubstitutor.Empty) {
                        val codeFragment =
                            createTypeCodeFragment(typeText.ifEmpty { StandardClassIds.Any.asFqNameString() }, baseFunction)
                        val ktType = analyze(codeFragment) {
                            codeFragment.getContentElement()?.type?.createPointer()
                        }?.restore()

                        if (ktType != null) {
                            val type = ktSubstitutor?.substitute(ktType) ?: ktType
                            val substitutedType = renderer.renderToString(type)
                            if (isReceiver && type is KaDefinitelyNotNullType) {
                                return createType("($substitutedType)")
                            }
                            return createType(substitutedType)
                        }
                    }
                }
            }
        }
    }
    return createType(typeText)
}

@KaExperimentalApi
context(_: KaSession)
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
                    substitution(it, iSubstitutor.substitute(typeCreator.typeParameterType(it)))
                }
                baseCallable.typeParameters.zip(inheritorCallable.typeParameters).forEach {
                    substitution(it.first, typeCreator.typeParameterType(it.second))
                }
            }
        }
    } else null
}