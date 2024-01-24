// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionNameSuggester
import org.jetbrains.kotlin.psi.KtElement

object KotlinNameSuggester : IExtractionNameSuggester<KtType> {
    override fun suggestNamesByType(
        kotlinType: KtType,
        container: KtElement,
        validator: (String) -> Boolean,
        defaultName: String?
    ): List<String> = with(KotlinNameSuggester()) {
        analyze(container) {
            if (kotlinType.isUnit) emptyList() else suggestTypeNames(kotlinType).filter(validator).toList()
        }
    }

    override fun createNameValidator(
        container: KtElement,
        anchor: PsiElement?,
        validatorType: KotlinNameSuggestionProvider.ValidatorTarget
    ): (String) -> Boolean = {
        analyze(container) {
            KotlinDeclarationNameValidator(
                container,
                true,
                validatorType,
            ).validate(it)
        }
    }

    override fun suggestNameByName(
        name: String,
        container: KtElement,
        anchor: PsiElement?
    ): String = KotlinNameSuggester.suggestNameByName(
        name,
        createNameValidator(
            container,
            anchor,
            KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER
        )
    )
}