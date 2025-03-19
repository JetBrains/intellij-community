// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi

import com.jetbrains.ml.api.feature.suspendable.FeatureProvider
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder


data class ImportRankingContext(
  val candidates: List<ImportCandidateHolder>
)

data class ImportCandidateContext(
  val candidates: List<ImportCandidateHolder>,
  val candidate: ImportCandidateHolder,
)


abstract class ImportRankingContextFeatures : FeatureProvider.InNamespace<ImportRankingContext>("import_candidates_list")

abstract class ImportCandidateFeatures : FeatureProvider.InNamespace<ImportCandidateContext>("import_candidate")
