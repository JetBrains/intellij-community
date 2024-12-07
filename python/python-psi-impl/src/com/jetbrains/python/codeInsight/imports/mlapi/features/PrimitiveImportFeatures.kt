// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.jetbrains.ml.features.api.feature.Feature
import com.jetbrains.ml.features.api.feature.FeatureDeclaration
import com.jetbrains.ml.features.api.feature.FeatureFilter
import com.jetbrains.ml.features.api.feature.extractFeatureDeclarations
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateContext
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateFeatures


object PrimitiveImportFeatures : ImportCandidateFeatures() {
  object Features {
    val RELEVANCE = FeatureDeclaration.int("old_relevance") {
      """
        Heuristic-based relevance computed in com.jetbrains.python.codeInsight.completion.PyCompletionUtilsKt.computeCompletionWeight
      """.trimIndent()
    }
    val COMPONENT_COUNT = FeatureDeclaration.int("number_of_dots") {
      "The amount of components in the import statement"
    }.nullable()
  }

  override val featureDeclarations = extractFeatureDeclarations(Features)

  override suspend fun computeFeatures(instance: ImportCandidateContext, filter: FeatureFilter): List<Feature>? = buildList {
    add(Features.RELEVANCE with instance.candidate.relevance)
    add(Features.COMPONENT_COUNT with instance.candidate.path?.componentCount)
  }
}
