package com.intellij.ml.local.models

import com.intellij.lang.Language
import com.intellij.ml.local.models.api.LocalModelBuilder
import com.intellij.ml.local.models.api.LocalModelFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.resolve.ResolveCache
import kotlin.system.measureTimeMillis


object LocalModelsTraining {
  private val LOG = logger<LocalModelsTraining>()

  @Volatile private var isTraining = false

  fun isTraining(): Boolean = isTraining

  fun train(project: Project, language: Language) = ApplicationManager.getApplication().executeOnPooledThread {
    val fileType = language.associatedFileType ?: throw IllegalArgumentException("Unsupported language")
    if (isTraining) {
      LOG.error("Local models training has already started.")
      return@executeOnPooledThread
    }
    isTraining = true
    val modelsManager = LocalModelsManager.getInstance(project)
    val files = getFiles(project, fileType)
    val factories = LocalModelFactory.forLanguage(language)
    val id2builder = factories.associate { it.id to it.modelBuilder(project, language) }
    id2builder.forEach { it.value.onStarted() }
    val ids = id2builder.keys.joinToString(", ")
    LOG.info("Local models training started for language ${language.id} and models: $ids")
    val ms = measureTimeMillis {
      processFiles(files, id2builder, project)
    }
    id2builder.forEach { it.value.onFinished() }
    LOG.info("Local models training finished for language ${language.id} and models: $ids. Duration: $ms ms")
    id2builder.forEach {
      it.value.build()?.let { model ->
        modelsManager.registerModel(language, model)
      }
    }
    isTraining = false
  }

  private fun processFiles(files: Set<Int>, modelBuilders: Map<String, LocalModelBuilder>, project: Project) {
    val psiManager = PsiManager.getInstance(project)
    val resolveCache = ResolveCache.getInstance(project)
    for (fileId in files) {
      runReadAction {
        val file = VirtualFileManager.getInstance().findFileById(fileId)
        psiManager.findFile(file)?.let { psiFile ->
          for (id2builder in modelBuilders) {
            try {
              psiFile.accept(id2builder.value.fileVisitor())
            }
            catch (e: Throwable) {
              LOG.warn("Local model training error. Model: ${id2builder.key}. File: ${file.path}.", e)
            }
          }
          resolveCache.clearCache(true)
        }
      }
    }
  }

  private fun getFiles(project: Project, fileType: FileType): Set<Int> {
    val index = ProjectFileIndex.getInstance(project)
    val fileIds = mutableSetOf<Int>()
    runReadAction {
      index.iterateContent(ContentIterator { file ->
        if (file is VirtualFileWithId && file.fileType == fileType) {
          fileIds.add(file.id)
        }
        true
      })
    }
    return fileIds
  }
}