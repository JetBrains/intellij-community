// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.jetbrains.ml.building.blocks.task.MLTask
import com.jetbrains.python.codeInsight.imports.mlapi.features.*


val MLTaskPyCharmImportStatementsRanking = MLTask(
  id = "pycharm_import_statements_ranking",
  treeFeaturesPerLevel = listOf(
    {
      featuresFromProviders(
        CandidatesListFeatures,
        BaseProjectFeatures
      )
    },
    {
      featuresFromProviders(
        ImportsFeatures,
        RelevanceEvaluationFeatures,
        ImportCandidateRelativeFeatures,
        NeighborFilesImportsFeatures,
        PrimitiveImportFeatures,
        PsiStructureFeatures,
        OpenFilesImportsFeatures,
      )
    }
  )
)
