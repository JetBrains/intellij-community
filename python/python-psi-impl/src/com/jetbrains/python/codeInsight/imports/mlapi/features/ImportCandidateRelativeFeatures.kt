// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.jetbrains.ml.api.feature.*
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateContext
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateFeatures

object ImportCandidateRelativeFeatures : ImportCandidateFeatures() {
  object Features {
    val RELATIVE_POSITION: FeatureDeclaration<Int> = FeatureDeclaration.int("original_position") {
      "The import's position without ML ranking"
    }
  }

  override val namespaceFeatureDeclarations: List<FeatureDeclaration<*>> = extractFeatureDeclarations(Features)

  override val featureComputationPolicy: FeatureComputationPolicy = FeatureComputationPolicy(tolerateRedundantFeatures = true, putNullImplicitly = true)

  override suspend fun computeNamespaceFeatures(instance: ImportCandidateContext, filter: FeatureFilter): List<Feature> = buildList<Feature> {
    add(Features.RELATIVE_POSITION with instance.candidates.indexOf(instance.candidate))
  }
}
