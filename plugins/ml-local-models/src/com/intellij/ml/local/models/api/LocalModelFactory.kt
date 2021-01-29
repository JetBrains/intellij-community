package com.intellij.ml.local.models.api

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.project.Project

interface LocalModelFactory {
  companion object {
    private val EP_NAME = LanguageExtension<LocalModelFactory>("com.intellij.ml.local.models.factory")

    fun forLanguage(language: Language): List<LocalModelFactory> {
      return EP_NAME.allForLanguage(language)
    }
  }
  val id: String

  fun modelBuilder(project: Project, language: Language): LocalModelBuilder
}