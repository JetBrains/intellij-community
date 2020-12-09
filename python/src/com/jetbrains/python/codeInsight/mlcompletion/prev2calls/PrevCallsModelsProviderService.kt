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
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.jetbrains.python.PythonPluginDisposable
import java.util.concurrent.ExecutionException

@Service
class PrevCallsModelsProviderService {
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

  private val modelsLoadingQueue = MergingUpdateQueue("ModelsLoadingQueue", 1000, true, null,
                                                      PythonPluginDisposable.getInstance(), null, false)
  private fun createUpdate(identity: Any, runnable: () -> Unit) = object : Update(identity) {
    override fun canEat(update: Update?) = this == update
    override fun run() = runnable()
  }

  fun loadModelFor(qualifierName: String) {
    val moduleName = qualifierName.substringBefore(".")
    if (!haveModelForQualifier(moduleName)) return

    val modelForPackage = package2model.getIfPresent(moduleName)

    fun tryLoadModel() {
      try {
        package2model.get(moduleName)
      } catch (ex: ExecutionException) {
        if (ex.cause !is ModelNotFoundException) {
          logger.error(ex)
        }
      }
    }

    if (modelForPackage == null) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        tryLoadModel()
      }
      else {
        modelsLoadingQueue.queue(createUpdate(moduleName) { tryLoadModel() })
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