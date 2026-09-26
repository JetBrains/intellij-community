// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports.features

import com.intellij.openapi.application.readAction
import com.intellij.platform.ml.logs.IJFeatureDeclarations
import com.intellij.psi.PsiElement
import com.jetbrains.mlapi.feature.Feature
import com.jetbrains.mlapi.feature.FeatureContainer
import com.jetbrains.mlapi.feature.FeatureDeclaration
import com.jetbrains.mlapi.feature.FeatureSet


object PsiStructureFeatures : ImportCandidateFeatures(Features) {
  object Features : FeatureContainer {
    val PSI_CLASS: FeatureDeclaration<Class<*>?> = IJFeatureDeclarations.aClass("importable_class") { "PSI class of the imported element" }.nullable()
    val PSI_PARENT: List<FeatureDeclaration<Class<*>?>> = (1..4).map { i -> IJFeatureDeclarations.aClass("psi_parent_$i") { "PSI parent #$i" }.nullable() }
  }

  override suspend fun computeNamespaceFeatures(instance: ImportCandidateContext, filter: FeatureSet): List<Feature> = buildList {
    readAction {
      add(Features.PSI_CLASS with (instance.candidate.importable?.javaClass))
      Features.PSI_PARENT.withIndex().forEach { (i, featureDeclaration) ->
        var parent: PsiElement? = instance.candidate.importable
        repeat(i) {
          parent = parent?.parent
        }
        add(featureDeclaration with parent?.javaClass)
      }
    }
  }
}
