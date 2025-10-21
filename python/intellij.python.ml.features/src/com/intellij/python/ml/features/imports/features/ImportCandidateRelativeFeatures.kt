// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports.features

import com.jetbrains.mlapi.feature.Feature
import com.jetbrains.mlapi.feature.FeatureContainer
import com.jetbrains.mlapi.feature.FeatureDeclaration
import com.jetbrains.mlapi.feature.FeatureSet

object ImportCandidateRelativeFeatures : ImportCandidateFeatures(Features) {
  object Features : FeatureContainer {
    val RELATIVE_POSITION: FeatureDeclaration<Int> = FeatureDeclaration.int("original_position") {
      "The import's position without ML ranking"
    }
  }

  override suspend fun computeNamespaceFeatures(instance: ImportCandidateContext, filter: FeatureSet): List<Feature> = buildList {
    add(Features.RELATIVE_POSITION with instance.candidates.indexOf(instance.candidate))
  }
}
