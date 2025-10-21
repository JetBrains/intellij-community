// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports.features

import com.jetbrains.mlapi.feature.FeatureComputationPolicy
import com.jetbrains.mlapi.feature.FeatureContainer
import com.jetbrains.mlapi.feature.suspendable.AsyncFeatureProvider
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder


data class ImportRankingContext(
  val candidates: List<ImportCandidateHolder>
)

data class ImportCandidateContext(
  val candidates: List<ImportCandidateHolder>,
  val candidate: ImportCandidateHolder,
)


abstract class ImportRankingContextFeatures(featuresContainer: FeatureContainer) :
  AsyncFeatureProvider.InNamespace<ImportRankingContext>(
    "import_candidates_list", '_',
    featuresContainer,
    FeatureComputationPolicy(true, true)
  )

abstract class ImportCandidateFeatures(featuresContainer: FeatureContainer) :
  AsyncFeatureProvider.InNamespace<ImportCandidateContext>(
    "import_candidate", '_',
    featuresContainer,
    FeatureComputationPolicy(true, true)
  )
