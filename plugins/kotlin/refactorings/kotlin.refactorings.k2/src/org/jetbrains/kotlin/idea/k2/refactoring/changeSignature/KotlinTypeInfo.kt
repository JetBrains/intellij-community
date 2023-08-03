// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.psi.*
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildSubstitutor
import org.jetbrains.kotlin.analysis.api.components.buildTypeParameterType
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

data class KotlinTypeInfo(var text: String?, val context: KtElement)

@OptIn(KtAllowAnalysisFromWriteAction::class, KtAllowAnalysisOnEdt::class)
internal fun KtPsiFactory.createType(
    typeText: String,
    inheritedCallable: KtCallableDeclaration?,
    baseFunction: PsiElement,
    variance: Variance
): KtTypeReference {
    if (inheritedCallable != null) {
        allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                analyze(inheritedCallable) {
                    if (baseFunction is PsiMethod) {
                        val substitutor = if (inheritedCallable is KtFunctionLiteral) {
                            val containingClass = baseFunction.containingClass
                            val functionalType = inheritedCallable.getExpectedType()?.asPsiType(inheritedCallable, true)
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
                    if (ktSubstitutor != null) {
                        val ktType = createExpressionCodeFragment("p as $typeText", baseFunction).getContentElement()?.getKtType()
                        if (ktType != null) {
                            val substitutedType = ktSubstitutor.substitute(ktType).render(position = variance)
                            return createType(substitutedType)
                        }
                    }
                }
            }
        }
    }
    return createType(typeText)
}

context(KtAnalysisSession)
private fun createSubstitutor(inheritorDeclaration: KtCallableDeclaration, baseFunction: PsiElement): KtSubstitutor? {
    val inheritorCallable = inheritorDeclaration.getSymbol()
    val baseCallable = (baseFunction as? KtCallableDeclaration)?.getSymbol()
        ?: (baseFunction as? PsiMember)?.getCallableSymbol() ?: return null
    val inheritor = inheritorCallable.getContainingSymbol()
    val base = baseCallable.getContainingSymbol()
    return if (inheritor is KtClassOrObjectSymbol && base is KtClassOrObjectSymbol) {
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