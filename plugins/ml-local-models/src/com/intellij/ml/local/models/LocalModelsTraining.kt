package com.intellij.ml.local.models

import com.intellij.lang.Language
import com.intellij.ml.local.MlLocalModelsBundle
import com.intellij.ml.local.models.api.LocalModelBuilder
import com.intellij.ml.local.models.api.LocalModelFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import java.lang.IllegalArgumentException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


object LocalModelsTraining {
  private val LOG = logger<LocalModelsTraining>()
  private const val FILES_CHUNK_SIZE = 100

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
    val task = object : Task.Backgroundable(project, MlLocalModelsBundle.message("ml.local.models.training.title"), true) {
      override fun run(indicator: ProgressIndicator) {
        val files = getFiles(project, fileType)
        val factories = LocalModelFactory.forLanguage(language)
        val id2builder = factories.associate { it.id to it.modelBuilder(project, language) }
        id2builder.forEach { it.value.onStarted() }
        val ids = id2builder.keys.joinToString(", ")
        LOG.info("Local models training started for language ${language.id} and models: $ids")
        processFiles(files, id2builder, project, indicator)
        id2builder.forEach { it.value.onFinished() }
        LOG.info("Local models training finished for language ${language.id} and models: $ids")
        id2builder.forEach {
          it.value.build()?.let { model ->
            modelsManager.registerModel(language, model)
          }
        }
      }

      override fun onFinished() {
        isTraining = false
      }
    }
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
  }

  private fun processFiles(files: List<VirtualFile>, modelBuilders: Map<String, LocalModelBuilder>, project: Project,
                           indicator: ProgressIndicator) {
    val dumbService = DumbService.getInstance(project)
    val psiManager = PsiManager.getInstance(project)
    val executorService = Executors.newFixedThreadPool((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1))
    indicator.isIndeterminate = false
    indicator.text = MlLocalModelsBundle.message("ml.local.models.training.files.processing")
    indicator.fraction = 0.0
    val processed = AtomicInteger(0)

    for (filesChunk in files.chunked(FILES_CHUNK_SIZE)) {
      executorService.submit {
        dumbService.runReadActionInSmartMode {
          for (file in filesChunk) {
            psiManager.findFile(file)?.let { psiFile ->
              for (id2builder in modelBuilders) {
                try {
                  psiFile.accept(id2builder.value.fileVisitor())
                }
                catch (e: Throwable) {
                  LOG.error("Local model training error. Model: ${id2builder.key}. File: ${file.path}.", e)
                }
              }
            }
            indicator.fraction = processed.incrementAndGet().toDouble() / files.size
          }
        }
      }
    }
    executorService.shutdown()

    try {
      while (true) {
        if (executorService.awaitTermination(1, TimeUnit.SECONDS)) {
          break
        }
        ProgressManager.checkCanceled()
      }
    }
    finally {
      executorService.shutdownNow()
    }
  }

  fun getFiles(project: Project, fileType: FileType): List<VirtualFile> = runReadAction {
    FileBasedIndex.getInstance().getContainingFiles(FileTypeIndex.NAME,
                                                    fileType,
                                                    GlobalSearchScope.projectScope(project)).toList()
  }
}