package com.intellij.searchEverywhereLucene.backend

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project


/**
 * The service is intended to be used instead of a project/application as a parent disposable.
 */
@Service( Service.Level.PROJECT)
internal class SearchEverywhereLucenePluginDisposable : Disposable {
  override fun dispose() {
  }

  companion object {
    fun getInstance(project: Project): Disposable {
      return project.getService(SearchEverywhereLucenePluginDisposable::class.java)
    }
  }
}