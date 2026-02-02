// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.util

import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.types.Variance

@OptIn(KaExperimentalApi::class)
fun KaSession.getExplicitLambdaSignature(element: KtLambdaExpression): String? {
    val lambdaSymbol = element.functionLiteral.symbol as KaFunctionSymbol
    val valueParameters = lambdaSymbol.valueParameters
    if (valueParameters.any { it.returnType is KaErrorType } ) return null
    return valueParameters.joinToString { param ->
        val parameter = param.psi as? KtParameter
        if (parameter != null) {
            (parameter.name ?: parameter.destructuringDeclaration?.text
            ?: "_") + ": " + param.returnType.render(position = Variance.IN_VARIANCE)
        } else param.render()
    }
}

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

fun specifyExplicitLambdaSignature(element: KtLambdaExpression, parameterString: String) {
    val psiFactory = KtPsiFactory(element.project)
    val functionLiteral = element.functionLiteral
    val newParameterList = (psiFactory.createExpression("{ $parameterString -> }") as KtLambdaExpression).functionLiteral.valueParameterList

    functionLiteral.setParameterListIfAny(psiFactory, newParameterList)

    for (parameter in element.valueParameters) {
        ShortenReferencesFacility.getInstance().shorten(parameter)
    }
}
