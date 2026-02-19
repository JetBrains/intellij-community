// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.cache

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.annotations.ApiStatus

/**
 * Motivation:
 * [com.intellij.python.community.impl.huggingFace.annotation.HuggingFaceIdentifierReferenceProvider]
 * may start providing references before the cache is filled -> therefore providing false negatives
 * for strings, matching HF model and dataset names.
 *
 * The refresh function restarts the reference provider after the cache is filled.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HuggingFaceCacheUpdateHandler(private val project: Project) : Disposable {
  private val connection: MessageBusConnection = project.messageBus.connect(this)

  init {
    connection.subscribe(HuggingFaceCacheUpdateListener.TOPIC, object : HuggingFaceCacheUpdateListener {
      override fun cacheUpdated() = refreshReferencesInProject()
    })
  }

  private fun refreshReferencesInProject() {
    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) { return@invokeLater }
      val fileEditorManager = FileEditorManager.getInstance(project)
      val psiDocumentManager = PsiDocumentManager.getInstance(project)
      val openFiles = fileEditorManager.openFiles.toList()
      psiDocumentManager.reparseFiles(openFiles, true)
    }
  }

  override fun dispose() = connection.disconnect()
}
