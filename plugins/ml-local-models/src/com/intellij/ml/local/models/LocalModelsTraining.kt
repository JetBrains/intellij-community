package com.intellij.ml.local.models

import com.intellij.lang.Language
import com.intellij.ml.local.MlLocalModelsBundle
import com.intellij.ml.local.models.api.LocalModelBuilder
import com.intellij.ml.local.models.api.LocalModelFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.resolve.ResolveCache
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis


object LocalModelsTraining {
  private val LOG = logger<LocalModelsTraining>()
  private const val MAX_FILE_PROCESSING_RETRIES = 10
  private const val DELAY_BETWEEN_RETRIES_IN_MS = 300L

  @Volatile private var isTraining = false

  fun isTraining(): Boolean = isTraining

  fun train(project: Project, language: Language) = ApplicationManager.getApplication().executeOnPooledThread {
    val fileType = language.associatedFileType ?: throw IllegalArgumentException("Unsupported language")
    if (isTraining) {
      LOG.error("Training has already started.")
      return@executeOnPooledThread
    }
    isTraining = true
    val modelsManager = LocalModelsManager.getInstance(project)
    val factories = LocalModelFactory.forLanguage(language)
    factories.forEach { modelsManager.unregisterModel(language, it.id) }
    val id2builder = factories.associate { it.id to it.modelBuilder(project, language) }

    val task = object : Task.Backgroundable(project, MlLocalModelsBundle.message("ml.local.models.training.title"), true) {
      override fun run(indicator: ProgressIndicator) {
        val files = getFiles(project, fileType)
        id2builder.forEach { it.value.onStarted() }
        val ids = id2builder.keys.joinToString(", ")
        LOG.info("Local models training started for language ${language.id} and models: $ids")
        val ms = measureTimeMillis {
          processFiles(files, id2builder, project, indicator)
        }
        markModelsFinished(LocalModelBuilder.FinishReason.SUCCESS)
        LOG.info("Training finished for language ${language.id} and models: $ids. Duration: $ms ms")
        id2builder.forEach {
          it.value.build()?.let { model ->
            modelsManager.registerModel(language, model)
          }
        }
      }

      override fun onCancel() = markModelsFinished(LocalModelBuilder.FinishReason.CANCEL)

      override fun onThrowable(error: Throwable) {
        markModelsFinished(LocalModelBuilder.FinishReason.ERROR)
        super.onThrowable(error)
      }

      private fun markModelsFinished(reason: LocalModelBuilder.FinishReason) = id2builder.forEach { it.value.onFinished(reason) }

      override fun onFinished() {
        isTraining = false
      }
    }
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
  }

  private fun processFiles(files: Set<Int>, modelBuilders: Map<String, LocalModelBuilder>, project: Project,
                           indicator: ProgressIndicator) {
    val resolveCache = ResolveCache.getInstance(project)
    val dumbService = DumbService.getInstance(project)
    indicator.isIndeterminate = false
    indicator.text = MlLocalModelsBundle.message("ml.local.models.training.files.processing")
    indicator.fraction = 0.0
    val processed = AtomicInteger(0)
    for (fileId in files) {
      for (id2builder in modelBuilders) {
        if (indicator.isCanceled) {
          throw ProcessCanceledException()
        }
        var retriesCount = 0
        while (retriesCount < MAX_FILE_PROCESSING_RETRIES &&
               !tryProcessFile(fileId, id2builder.key, id2builder.value.fileVisitor(), project)) {
          if (indicator.isCanceled) {
            throw ProcessCanceledException()
          }
          if (dumbService.isDumb) {
            dumbService.waitForSmartMode()
          } else {
            try {
              TimeUnit.MILLISECONDS.sleep(DELAY_BETWEEN_RETRIES_IN_MS)
            } catch (ignore: InterruptedException) { }
          }
          retriesCount++
          ProgressIndicatorUtils.yieldToPendingWriteActions(indicator)
        }
        if (retriesCount >= MAX_FILE_PROCESSING_RETRIES) {
          LOG.warn("Too much retries to process file with id: $fileId. Model: ${id2builder.key}")
        }
      }
      resolveCache.clearCache(true)
      indicator.fraction = processed.incrementAndGet().toDouble() / files.size
    }
  }

  private fun tryProcessFile(fileId: Int, modelId: String, fileVisitor: PsiElementVisitor, project: Project): Boolean =
    ProgressIndicatorUtils.runInReadActionWithWriteActionPriority {
      if (DumbService.isDumb(project)) {
        throw ProcessCanceledException()
      }
      VirtualFileManager.getInstance().findFileById(fileId)?.let { file ->
        PsiManager.getInstance(project).findFile(file)?.let { psiFile ->
          try {
            psiFile.accept(fileVisitor)
          }
          catch (pce: ProcessCanceledException) {
            throw pce
          }
          catch (e: Throwable) {
            LOG.warn("Model: $modelId. File: ${file.path}.", e)
          }
        }
      }
    }

  private fun getFiles(project: Project, fileType: FileType): Set<Int> {
    val index = ProjectFileIndex.getInstance(project)
    val fileIds = mutableSetOf<Int>()
    runReadAction {
      index.iterateContent(ContentIterator { file ->
        if (file is VirtualFileWithId && FileTypeRegistry.getInstance().isFileOfType(file, fileType)) {
          fileIds.add(file.id)
        }
        true
      })
    }
    return fileIds
  }
}