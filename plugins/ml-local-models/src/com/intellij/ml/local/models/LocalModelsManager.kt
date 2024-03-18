package com.intellij.ml.local.models

import com.intellij.ml.local.models.api.LocalModel
import com.intellij.lang.Language
import com.intellij.ml.local.models.api.LocalModelFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class LocalModelsManager private constructor(private val project: Project) {
  companion object {
    fun getInstance(project: Project): LocalModelsManager = project.getService(LocalModelsManager::class.java)
  }
  private val models = mutableMapOf<String, MutableMap<String, LocalModel?>>()

  fun getModels(language: Language): List<LocalModel> {
    val id2model = models.getOrPut(language.id) { mutableMapOf() }
    for (factory in LocalModelFactory.forLanguage(language)) {
      if (factory.id !in id2model) {
        id2model[factory.id] = factory.modelBuilder(project, language).build()
      }
    }
    return id2model.values.filterNotNull()
  }

  fun registerModel(language: Language, model: LocalModel) {
    models.getOrPut(language.id, { mutableMapOf() })[model.id] = model
  }

  fun unregisterModel(language: Language, modelId: String) {
    models[language.id]?.remove(modelId)
  }

  inline fun <reified T : LocalModel> getModel(language: Language): T? = getModels(language).filterIsInstance<T>().firstOrNull()
}