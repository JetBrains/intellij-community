// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.mlCompletion

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.idea.base.codeInsight.contributorClass

internal class KotlinElementFeatureProvider : ElementFeatureProvider {

    override fun getName(): String = "kotlin"

    override fun calculateFeatures(
        element: LookupElement,
        location: CompletionLocation,
        contextFeatures: ContextFeatures,
    ): Map<String, MLFeatureValue> {
        val contributor = element.contributorClass
            ?: return emptyMap()

        return mapOf(
            "k2_contributor" to MLFeatureValue.className(contributor),
        )
    }
}