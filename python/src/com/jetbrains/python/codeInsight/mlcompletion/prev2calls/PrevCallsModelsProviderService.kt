// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion.prev2calls

import com.completion.features.models.prevcalls.python.PrevCallsModel
import com.completion.features.models.prevcalls.python.PrevCallsModelsLoader
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.update.DebouncedUpdates
import com.jetbrains.python.PythonPluginDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ExecutionException
import kotlin.time.Duration.Companion.seconds

@Service
class PrevCallsModelsProviderService(coroutineScope: CoroutineScope) {
  internal class ModelNotFoundException: Exception()
  private val logger = Logger.getInstance(PrevCallsModelsProviderService::class.java)

  private val package2model = CacheBuilder.newBuilder()
    .softValues()
    .maximumSize(40)
    .build(object: CacheLoader<String, PrevCallsModel>() {
      override fun load(expression: String): PrevCallsModel {
        val result = PrevCallsModelsLoader.getModelForExpression(expression)
        if (result == null) throw ModelNotFoundException()
        return result
      }
    })

  private val modelsLoadingQueue = DebouncedUpdates.forScope<String>(coroutineScope, "ModelsLoadingQueue", 1.seconds)
    .withContext(Dispatchers.Default)
    .runBatched { moduleNames -> loadModels(moduleNames) }
    .cancelOnDispose(PythonPluginDisposable.getInstance())

  fun loadModelFor(qualifierName: String) {
    val moduleName = qualifierName.substringBefore(".")
    if (!haveModelForQualifier(moduleName)) return

    val modelForPackage = package2model.getIfPresent(moduleName)

    if (modelForPackage == null) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        tryLoadModel(moduleName)
      }
      else {
        modelsLoadingQueue.queue(moduleName)
      }
    }
  }

  private fun loadModels(moduleNames: List<String>) {
    moduleNames.distinct().forEach { moduleName ->
      tryLoadModel(moduleName)
    }
  }

  private fun tryLoadModel(moduleName: String) {
    try {
      package2model.get(moduleName)
    } catch (ex: ExecutionException) {
      if (ex.cause !is ModelNotFoundException) {
        logger.error(ex)
      }
    }
  }

  fun getModelFor(qualifierName: String) = package2model.getIfPresent(qualifierName)

  fun haveModelForQualifier(qualifier: String) = PrevCallsModelsLoader.haveModule(qualifier.substringBefore("."))

  companion object {
    val instance: PrevCallsModelsProviderService
          get() = service()
  }
}