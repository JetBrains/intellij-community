// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports.features

import com.jetbrains.mlapi.feature.Feature
import com.jetbrains.mlapi.feature.FeatureContainer
import com.jetbrains.mlapi.feature.FeatureDeclaration
import com.jetbrains.mlapi.feature.FeatureSet

object CandidatesListFeatures : ImportRankingContextFeatures(Features) {
  object Features : FeatureContainer {
    val LENGTH: FeatureDeclaration<Int> = FeatureDeclaration.int("n_candidates") {
      "The amount of import candidates"
    }
    val HIGHEST_OLD_RELEVANCE: FeatureDeclaration<Int?> = FeatureDeclaration.int("highest_old_relevance") {
      "The highest heuristic-based relevance com.jetbrains.python.codeInsight.completion.PyCompletionUtilsKt.computeCompletionWeight"
    }.nullable()
  }

  override suspend fun computeNamespaceFeatures(instance: ImportRankingContext, filter: FeatureSet): List<Feature> = buildList {
    add(Features.LENGTH with instance.candidates.size)
    add(Features.HIGHEST_OLD_RELEVANCE with instance.candidates.maxOfOrNull { it.relevance })
  }
}
