// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.jetbrains.ml.*
import com.jetbrains.python.codeInsight.imports.mlapi.MLUnitImportCandidate
import com.jetbrains.python.codeInsight.imports.mlapi.MLUnitImportCandidatesList

class ImportCandidateRelativeFeatures : FeatureProvider(MLUnitImportCandidate) {
  object Features {
    val RELATIVE_POSITION = FeatureDeclaration.int("original_position") {
      "The import's position without ML ranking"
    }
  }

  override val unitSight = setOf(
    MLUnitImportCandidatesList
  )

  override val featureDeclarations = extractFieldsAsFeatureDeclarations(Features)

  override suspend fun computeFeatures(units: MLUnitsMap, usefulFeaturesFilter: FeatureFilter) = buildList<Feature> {
    val candidate = units[MLUnitImportCandidate]
    val allCandidates = units[MLUnitImportCandidatesList]

    add(Features.RELATIVE_POSITION with allCandidates.indexOf(candidate))
  }
}
