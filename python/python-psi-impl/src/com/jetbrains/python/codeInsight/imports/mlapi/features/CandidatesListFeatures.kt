// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.jetbrains.ml.*
import com.jetbrains.python.codeInsight.imports.mlapi.MLUnitImportCandidatesList

class CandidatesListFeatures : FeatureProvider(MLUnitImportCandidatesList) {
  object Features {
    val LENGTH = FeatureDeclaration.int("n_candidates") {
      "The amount of import candidates"
    }
    val HIGHEST_OLD_RELEVANCE = FeatureDeclaration.int("highest_old_relevance") {
      "The highest heuristic-based relevance com.jetbrains.python.codeInsight.completion.PyCompletionUtilsKt.computeCompletionWeight"
    }.nullable()
  }

  override val featureDeclarations = extractFieldsAsFeatureDeclarations(Features)

  override suspend fun computeFeatures(units: MLUnitsMap, usefulFeaturesFilter: FeatureFilter) = buildList<Feature> {
    val candidates = units[MLUnitImportCandidatesList]
    add(Features.LENGTH with candidates.size)
    add(Features.HIGHEST_OLD_RELEVANCE with candidates.maxOfOrNull { it.relevance })
  }
}
