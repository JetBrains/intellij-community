// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.jetbrains.ml.api.feature.Feature
import com.jetbrains.ml.api.feature.FeatureDeclaration
import com.jetbrains.ml.api.feature.FeatureFilter
import com.jetbrains.ml.api.feature.FeatureValueType
import com.jetbrains.ml.api.feature.suspendable.FeatureProvider
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateContext
import com.jetbrains.python.codeInsight.imports.mlapi.ImportRankingContext
import com.jetbrains.python.codeInsight.imports.mlapi.features.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.collections.plus


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
      async {
        provider.computeFeaturesWithImplicitNull(instance, filter)
      }
    }
    jobs.flatMap { it.await() }
  }

  suspend fun computeCandidateFeatures(instance: ImportCandidateContext, filter: FeatureFilter): List<Feature> = coroutineScope {
    val jobs = candidate.map { provider ->
      async {
        provider.computeFeaturesWithImplicitNull(instance, filter)
      }
    }
    jobs.flatMap { it.await() }
  }

  private suspend fun <T : Any> FeatureProvider<T>.computeFeaturesWithImplicitNull(
    instance: T,
    filter: FeatureFilter
  ): List<Feature> {
    val features = provideFeatures(instance, filter)
    if (!featureComputationPolicy.putNullImplicitly) {
      return features
    }
    val implicitNullFeatures = mutableListOf<Feature>()
    for (declaration in featureDeclarations) {
      if (declaration.isNullable() && features.all { feature -> feature.declaration != declaration }) {
        implicitNullFeatures.add(declaration.asNullable() with null)
      }
    }
    return features + implicitNullFeatures
  }

  private fun FeatureDeclaration<*>.isNullable(): Boolean = type is FeatureValueType.Nullable<*>

  @Suppress("UNCHECKED_CAST")
  private fun <T> FeatureDeclaration<T>.asNullable(): FeatureDeclaration<T?> = this as FeatureDeclaration<T?>

  val declarations: List<List<FeatureDeclaration<*>>> by lazy {
    listOf(context.flatMap { it.featureDeclarations }, candidate.flatMap { it.featureDeclarations })
  }
}
