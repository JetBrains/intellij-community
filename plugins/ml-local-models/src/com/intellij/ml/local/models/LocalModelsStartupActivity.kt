package com.intellij.ml.local.models

import com.intellij.lang.Language
import com.intellij.ml.local.models.api.LocalModelFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class LocalModelsStartupActivity : StartupActivity {
  override fun runActivity(project: Project) {
    val modelsManager = LocalModelsManager.getInstance(project)
    for (language in Language.getRegisteredLanguages()) {
      val factories = LocalModelFactory.forLanguage(language)
      for (factory in factories) {
        factory.modelBuilder(project, language).build()?.let { model ->
          modelsManager.registerModel(language, model)
        }
      }
    }
  }
}