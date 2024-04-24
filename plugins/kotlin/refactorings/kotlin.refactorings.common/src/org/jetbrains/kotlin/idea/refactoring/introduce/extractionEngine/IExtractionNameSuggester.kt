// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.psi.KtElement

interface IExtractionNameSuggester<KotlinType> {
    fun suggestNamesByType(
        kotlinType: KotlinType,
        container: KtElement,
        validator: (String) -> Boolean,
        defaultName: String? = null
    ): List<String>

    fun createNameValidator(
        container: KtElement,
        anchor: PsiElement?,
        validatorType: KotlinNameSuggestionProvider.ValidatorTarget
    ): (String) -> Boolean

    fun suggestNameByName(name: String, container: KtElement, anchor: PsiElement?): String

}