// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.jetbrains.ml.features.api.feature.Feature
import com.jetbrains.ml.features.api.feature.FeatureDeclaration
import com.jetbrains.ml.features.api.feature.FeatureFilter
import com.jetbrains.ml.features.api.feature.extractFeatureDeclarations
import com.jetbrains.python.codeInsight.imports.mlapi.ImportRankingContext
import com.jetbrains.python.codeInsight.imports.mlapi.ImportRankingContextFeatures

object CandidatesListFeatures : ImportRankingContextFeatures() {
  object Features {
    val LENGTH = FeatureDeclaration.int("n_candidates") {
      "The amount of import candidates"
    }
    val HIGHEST_OLD_RELEVANCE = FeatureDeclaration.int("highest_old_relevance") {
      "The highest heuristic-based relevance com.jetbrains.python.codeInsight.completion.PyCompletionUtilsKt.computeCompletionWeight"
    }.nullable()
  }

  override val featureDeclarations = extractFeatureDeclarations(Features)

  override suspend fun computeFeatures(instance: ImportRankingContext, filter: FeatureFilter): List<Feature> = buildList {
    add(Features.LENGTH with instance.candidates.size)
    add(Features.HIGHEST_OLD_RELEVANCE with instance.candidates.maxOfOrNull { it.relevance })
  }
}
