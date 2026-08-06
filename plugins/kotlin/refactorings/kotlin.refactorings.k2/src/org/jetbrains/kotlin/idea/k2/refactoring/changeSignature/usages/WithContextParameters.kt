// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory

interface WithContextParameters {
    fun getContextParametersValues( changeInfo: KotlinChangeInfoBase): List<String>
    fun wrapIntoContextValues(
        element: KtElement,
        contextValues: List<String>,
        psiFactory: KtPsiFactory,
        expandToElementToWrap: (KtElement) -> KtElement = { it },
    ): KtElement? {
        if (contextValues.isEmpty()) return null

        val useContextFunctionForContextValues = element.languageVersionSettings.apiVersion >= ApiVersion.KOTLIN_2_2

        fun wrapInto(currentElement: KtElement, pattern: (String) -> String): KtElement {
            val elementToWrap = expandToElementToWrap(currentElement)
            elementToWrap.qualifyNestedThisExpressions()
            return elementToWrap.replace(psiFactory.createExpression(pattern(elementToWrap.text))) as KtElement
        }

        if (useContextFunctionForContextValues) {
            return wrapInto(element) { text -> "context(${contextValues.joinToString()}) {\n$text\n}" }
        }

        var newElement = element
        for (contextValue in contextValues) {
            newElement = wrapInto(newElement) { text -> "with($contextValue) {\n$text\n}" }
        }
        return newElement
    }
}