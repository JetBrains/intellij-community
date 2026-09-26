// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidatorProvider
import org.jetbrains.kotlin.psi.KtElement

interface IExtractionNameSuggester<KotlinType> {

    fun suggestNamesByType(
        kotlinType: KotlinType,
        container: KtElement,
        validator: KotlinNameValidator,
        defaultName: @NonNls String? = null,
    ): List<@NonNls String> // todo return an actual Sequence

    fun suggestNameByName(
        name: @NonNls String,
        validator: KotlinNameValidator,
    ): @NonNls String
}

fun IExtractionNameSuggester<*>.suggestNameByName(
    name: @NonNls String,
    container: KtElement,
    anchor: PsiElement?,
): @NonNls String {
    val validator = KotlinNameValidatorProvider.getInstance()
        .createNameValidator(
            container = container,
            target = KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER,
            anchor = anchor,
        )

    return suggestNameByName(name, validator)
}
