// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.jetbrains.ml.*
import com.jetbrains.python.codeInsight.imports.mlapi.MLUnitImportCandidate


class PrimitiveImportFeatures : FeatureProvider(MLUnitImportCandidate) {
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

  override val featureDeclarations = extractFieldsAsFeatureDeclarations(Features)

  override suspend fun computeFeatures(units: MLUnitsMap, usefulFeaturesFilter: FeatureFilter) = buildList {

    val importCandidate = units[MLUnitImportCandidate]

    add(Features.RELEVANCE with importCandidate.relevance)
    add(Features.COMPONENT_COUNT with importCandidate.path?.componentCount)
  }
}
