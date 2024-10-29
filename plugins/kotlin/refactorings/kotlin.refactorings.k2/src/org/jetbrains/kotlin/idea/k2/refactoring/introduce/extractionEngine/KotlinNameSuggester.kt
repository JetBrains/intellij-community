// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidator
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionNameSuggester
import org.jetbrains.kotlin.psi.KtElement

object KotlinNameSuggester : IExtractionNameSuggester<KaType> {

    override fun suggestNamesByType(
        kotlinType: KaType,
        container: KtElement,
        validator: KotlinNameValidator,
        defaultName: String?,
    ): List<String> = analyze(container) {
        if (kotlinType.isUnitType) emptyList()
        else KotlinNameSuggester()
            .suggestTypeNames(kotlinType)
            .map { KotlinNameSuggester.suggestNameByName(it, validator) }
            .toList()
            .takeIf { it.isNotEmpty() } ?: listOf(KotlinNameSuggester.suggestNameByName(defaultName ?: "p", validator))
    }

    override fun suggestNameByName(
        name: String,
        validator: KotlinNameValidator,
    ): String = KotlinNameSuggester.suggestNameByName(name, validator)
}