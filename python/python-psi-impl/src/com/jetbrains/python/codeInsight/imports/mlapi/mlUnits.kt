// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi

import com.jetbrains.ml.features.api.MLUnit
import com.jetbrains.ml.features.api.feature.FeatureProvider
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder


data class ImportRankingContext(
  val candidates: List<ImportCandidateHolder>
)

data class ImportCandidateContext(
  val candidates: List<ImportCandidateHolder>,
  val candidate: ImportCandidateHolder,
)

val MLUnitImportRankingContext = MLUnit<ImportRankingContext>("import_candidates_list")

val MLUnitImportCandidate = MLUnit<ImportCandidateContext>("import_candidate")


abstract class ImportRankingContextFeatures : FeatureProvider<ImportRankingContext>(MLUnitImportRankingContext)

abstract class ImportCandidateFeatures : FeatureProvider<ImportCandidateContext>(MLUnitImportCandidate)
