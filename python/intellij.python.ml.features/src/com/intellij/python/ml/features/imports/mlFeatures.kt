// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.jetbrains.ml.api.feature.Feature
import com.jetbrains.ml.api.feature.FeatureDeclaration
import com.jetbrains.ml.api.feature.FeatureFilter
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateContext
import com.jetbrains.python.codeInsight.imports.mlapi.ImportRankingContext
import com.jetbrains.python.codeInsight.imports.mlapi.features.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope


internal object FeaturesRegistry {
  private val context = listOf(
    CandidatesListFeatures,
    BaseProjectFeatures
  )

  private val candidate = listOf(
    ImportsFeatures,
    RelevanceEvaluationFeatures,
    ImportCandidateRelativeFeatures,
    NeighborFilesImportsFeatures,
    PrimitiveImportFeatures,
    PsiStructureFeatures,
    OpenFilesImportsFeatures,
  )

  suspend fun computeContextFeatures(instance: ImportRankingContext, filter: FeatureFilter): List<Feature> = coroutineScope {
    val jobs = context.map { provider ->
      async { provider.provideFeatures(instance, filter) }
    }
    jobs.flatMap { it.await() }
  }

  suspend fun computeCandidateFeatures(instance: ImportCandidateContext, filter: FeatureFilter): List<Feature> = coroutineScope {
    val jobs = candidate.map { provider ->
      async { provider.provideFeatures(instance, filter) }
    }
    jobs.flatMap { it.await() }
  }

  val declarations: List<List<FeatureDeclaration<*>>> by lazy {
    listOf(context.flatMap { it.featureDeclarations }, candidate.flatMap { it.featureDeclarations })
  }
}
