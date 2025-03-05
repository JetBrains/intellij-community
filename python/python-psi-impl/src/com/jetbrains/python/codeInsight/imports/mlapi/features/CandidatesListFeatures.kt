// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.jetbrains.ml.api.feature.*
import com.jetbrains.python.codeInsight.imports.mlapi.ImportRankingContext
import com.jetbrains.python.codeInsight.imports.mlapi.ImportRankingContextFeatures

object CandidatesListFeatures : ImportRankingContextFeatures() {
  object Features {
    val LENGTH: FeatureDeclaration<Int> = FeatureDeclaration.int("n_candidates") {
      "The amount of import candidates"
    }
    val HIGHEST_OLD_RELEVANCE: FeatureDeclaration<Int?> = FeatureDeclaration.int("highest_old_relevance") {
      "The highest heuristic-based relevance com.jetbrains.python.codeInsight.completion.PyCompletionUtilsKt.computeCompletionWeight"
    }.nullable()
  }

  override val namespaceFeatureDeclarations: List<FeatureDeclaration<*>> = extractFeatureDeclarations(Features)

  override val featureComputationPolicy: FeatureComputationPolicy = FeatureComputationPolicy(tolerateRedundantFeatures = true, putNullImplicitly = true)

  override suspend fun computeNamespaceFeatures(instance: ImportRankingContext, filter: FeatureFilter): List<Feature> = buildList {
    add(Features.LENGTH with instance.candidates.size)
    add(Features.HIGHEST_OLD_RELEVANCE with instance.candidates.maxOfOrNull { it.relevance })
  }
}
