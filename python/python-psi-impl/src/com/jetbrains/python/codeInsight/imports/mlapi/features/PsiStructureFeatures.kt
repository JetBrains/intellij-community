// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiElement
import com.jetbrains.ml.api.feature.*
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateContext
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateFeatures


object PsiStructureFeatures : ImportCandidateFeatures() {
  object Features {
    val PSI_CLASS: FeatureDeclaration<Class<*>?> = FeatureDeclaration.aClass("importable_class") { "PSI class of the imported element" }.nullable()
    val PSI_PARENT: List<FeatureDeclaration<Class<*>?>> = (1..4).map { i -> FeatureDeclaration.aClass("psi_parent_$i") { "PSI parent #$i" }.nullable() }
  }

  override val namespaceFeatureDeclarations: List<FeatureDeclaration<*>> = extractFeatureDeclarations(Features)

  override val featureComputationPolicy: FeatureComputationPolicy = FeatureComputationPolicy(tolerateRedundantFeatures = true, putNullImplicitly = true)

  override suspend fun computeNamespaceFeatures(instance: ImportCandidateContext, filter: FeatureFilter): List<Feature> = buildList {
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
