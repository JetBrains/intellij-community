// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports.features

import com.jetbrains.mlapi.feature.Feature
import com.jetbrains.mlapi.feature.FeatureDeclaration
import com.jetbrains.mlapi.feature.FeatureSet
import com.jetbrains.mlapi.feature.suspendable.AsyncFeatureProvider
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

  suspend fun computeContextFeatures(instance: ImportRankingContext, filter: FeatureSet): List<Feature> = coroutineScope {
    val jobs = context.map { provider ->
      async {
        provider.computeFeaturesWithImplicitNull(instance, filter)
      }
    }
    jobs.flatMap { it.await() }
  }

  suspend fun computeCandidateFeatures(instance: ImportCandidateContext, filter: FeatureSet): List<Feature> = coroutineScope {
    val jobs = candidate.map { provider ->
      async {
        provider.computeFeaturesWithImplicitNull(instance, filter)
      }
    }
    jobs.flatMap { it.await() }
  }

  private suspend fun <T : Any> AsyncFeatureProvider<T>.computeFeaturesWithImplicitNull(
    instance: T,
    filter: FeatureSet
  ): Collection<Feature> {
    val features = provideFeatures(instance, filter)
    //if (!featureComputationPolicy.putNullImplicitly) {
    //  return features
    //}
    val implicitNullFeatures = mutableListOf<Feature>()
    for (declaration in featureDeclarations) {
      if (declaration.isNullable && !filter.contains(declaration)) {
        implicitNullFeatures.add(declaration.asNullable() with null)
      }
    }
    return features + implicitNullFeatures
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> FeatureDeclaration<T>.asNullable(): FeatureDeclaration<T?> = this as FeatureDeclaration<T?>

  val declarations: List<List<FeatureDeclaration<*>>> by lazy {
    listOf(context.flatMap { it.featureDeclarations }, candidate.flatMap { it.featureDeclarations })
  }
}
