// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports.features

import com.jetbrains.ml.api.feature.*


object PrimitiveImportFeatures : ImportCandidateFeatures() {
  object Features {
    val RELEVANCE: FeatureDeclaration<Int> = FeatureDeclaration.int("old_relevance") {
      """
        Heuristic-based relevance computed in com.jetbrains.python.codeInsight.completion.PyCompletionUtilsKt.computeCompletionWeight
      """.trimIndent()
    }
    val COMPONENT_COUNT: FeatureDeclaration<Int?> = FeatureDeclaration.int("number_of_dots") {
      "The amount of components in the import statement"
    }.nullable()
  }

  override val namespaceFeatureDeclarations: List<FeatureDeclaration<*>> = extractFeatureDeclarations(Features)

  override val featureComputationPolicy: FeatureComputationPolicy = FeatureComputationPolicy(tolerateRedundantFeatures = true, putNullImplicitly = true)

  override suspend fun computeNamespaceFeatures(instance: ImportCandidateContext, filter: FeatureSet): List<Feature> = buildList {
    add(Features.RELEVANCE with instance.candidate.relevance)
    add(Features.COMPONENT_COUNT with instance.candidate.path?.componentCount)
  }
}
