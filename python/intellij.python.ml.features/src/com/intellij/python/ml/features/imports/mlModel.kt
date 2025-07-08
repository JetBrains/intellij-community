// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.ml.models.PythonImportsRankingModelHolder
import com.jetbrains.ml.tools.model.MLModel
import com.jetbrains.ml.tools.model.catboost.prediction.CatBoostRegressionResult
import com.jetbrains.ml.tools.model.pipeline.ModelPipeline
import com.jetbrains.ml.tools.model.pipeline.ModelPipelineLoader
import java.util.concurrent.CompletableFuture


@Service(Service.Level.APP)
internal class ImportsRankingModelService {

  init {
    loadModel()
  }

  private var modelFuture: CompletableFuture<Void>? = null

  var model: ModelPipeline<MLModel<CatBoostRegressionResult>, CatBoostRegressionResult>? = null
    private set

  private fun loadModel() {
    modelFuture?.cancel(true)

    LOG.info("Loading CatBoost Imports Ranking model")
    modelFuture = ModelPipelineLoader.load(
      PythonImportsRankingModelHolder.getStream(),
      AppExecutorUtil.getAppExecutorService()
    ).thenAccept { model ->
      LOG.info("Successfully loaded imports ranking model")
      this.model = model.predicting<CatBoostRegressionResult>()
    }.exceptionally { e ->
      LOG.warn("Failed to load CatBoost imports ranking model", e)
      null
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): ImportsRankingModelService = service()

    private val LOG = logger<ImportsRankingModelService>()
  }
}

private class QuickfixRankingModelLoading : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!service<FinalImportRankingStatusService>().shouldLoadModel) return
    ImportsRankingModelService.getInstance()
  }
}
