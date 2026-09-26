package com.intellij.ml.local.models

import com.intellij.lang.Language
import com.intellij.ml.local.models.api.LocalModel
import com.intellij.ml.local.models.api.LocalModelFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class LocalModelsManager private constructor(private val project: Project) {
  companion object {
    fun getInstance(project: Project): LocalModelsManager = project.getService(LocalModelsManager::class.java)
  }
  private val models = ConcurrentHashMap<String, MutableMap<String, Optional<LocalModel>>>()

  fun getModels(language: Language): List<LocalModel> {
    val id2model = models.computeIfAbsent(language.id) { ConcurrentHashMap() }
    for (factory in LocalModelFactory.forLanguage(language)) {
      id2model.computeIfAbsent(factory.id) { Optional.ofNullable(factory.modelBuilder(project, language).build()) }
    }
    return id2model.values.mapNotNull { it.orElse(null) }
  }

  fun registerModel(language: Language, model: LocalModel) {
    models.computeIfAbsent(language.id) { ConcurrentHashMap() }[model.id] = Optional.of(model)
  }

  fun unregisterModel(language: Language, modelId: String) {
    models[language.id]?.remove(modelId)
  }

  inline fun <reified T : LocalModel> getModel(language: Language): T? = getModels(language).filterIsInstance<T>().firstOrNull()
}