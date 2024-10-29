// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiElement
import com.jetbrains.ml.*
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder
import com.jetbrains.python.codeInsight.imports.mlapi.MLUnitImportCandidate


class PsiStructureFeatures : FeatureProvider(MLUnitImportCandidate) {
  object Features {
    val PSI_CLASS = FeatureDeclaration.aClass("importable_class") { "PSI class of the imported element" }.nullable()
    val PSI_PARENT = (1..4).map { i -> FeatureDeclaration.aClass("psi_parent_$i") { "PSI parent #$i" }.nullable() }
  }

  override val featureDeclarations = extractFieldsAsFeatureDeclarations(Features)

  override suspend fun computeFeatures(units: MLUnitsMap, usefulFeaturesFilter: FeatureFilter) = buildList {
    val importCandidate: ImportCandidateHolder = units[MLUnitImportCandidate]

    readAction {
      add(Features.PSI_CLASS with (importCandidate.importable?.javaClass))
      Features.PSI_PARENT.withIndex().forEach { (i, featureDeclaration) ->
        var parent: PsiElement? = importCandidate.importable
        repeat(i) {
          parent = parent?.parent
        }
        add(featureDeclaration with parent?.javaClass)
      }
    }
  }
}
