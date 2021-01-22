package com.intellij.ml.local.models

import com.intellij.ml.local.models.api.LocalModel
import com.intellij.lang.Language
import com.intellij.openapi.project.Project

class LocalModelsManager private constructor(private val project: Project) {
  companion object {
    fun getInstance(project: Project): LocalModelsManager = project.getService(LocalModelsManager::class.java)
  }
  private val models = mutableMapOf<String, MutableList<LocalModel>>()

  fun getModels(language: Language): List<LocalModel> = models.getOrDefault(language.id, emptyList())

  fun registerModel(language: Language, model: LocalModel) {
    models.getOrPut(language.id, { mutableListOf() }).add(model)
  }

  inline fun <reified T : LocalModel> getModel(language: Language): T? = getModels(language).filterIsInstance<T>().firstOrNull()
}