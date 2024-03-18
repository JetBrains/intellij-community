// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.types.Variance

context(KtAnalysisSession)
fun getExplicitLambdaSignature(element: KtLambdaExpression): String? {
    val lambdaSymbol = element.functionLiteral.getSymbol() as KtFunctionLikeSymbol
    val valueParameters = lambdaSymbol.valueParameters
    if (valueParameters.any { it.returnType is KtErrorType } ) return null
    return valueParameters.joinToString { param ->
        val parameter = param.psi as? KtParameter
        if (parameter != null) {
            (parameter.name ?: parameter.destructuringDeclaration?.text
            ?: "_") + ": " + param.returnType.render(position = Variance.IN_VARIANCE)
        } else param.render()
    }
}

fun specifyExplicitLambdaSignature(element: KtLambdaExpression) {
    val parameterString = analyze(element) { getExplicitLambdaSignature(element) } ?: return
    specifyExplicitLambdaSignature(element, parameterString)
}

fun specifyExplicitLambdaSignature(element: KtLambdaExpression, parameterString: String) {
    val psiFactory = KtPsiFactory(element.project)
    val functionLiteral = element.functionLiteral
    val newParameterList = (psiFactory.createExpression("{ $parameterString -> }") as KtLambdaExpression).functionLiteral.valueParameterList

    fun KtFunctionLiteral.setParameterListIfAny(psiFactory: KtPsiFactory, newParameterList: KtParameterList?) {
        val oldParameterList = valueParameterList
        if (oldParameterList != null && newParameterList != null) {
            oldParameterList.replace(newParameterList)
        } else {
            val openBraceElement = lBrace
            val nextSibling = openBraceElement.nextSibling
            val addNewline = nextSibling is PsiWhiteSpace && nextSibling.text?.contains("\n") ?: false
            val (whitespace, arrow) = psiFactory.createWhitespaceAndArrow()
            addRangeAfter(whitespace, arrow, openBraceElement)
            if (newParameterList != null) {
                addAfter(newParameterList, openBraceElement)
            }

            if (addNewline) {
                addAfter(psiFactory.createNewLine(), openBraceElement)
            }
        }
    }
    functionLiteral.setParameterListIfAny(psiFactory, newParameterList)
    shortenReferences(element.valueParameters)
}
