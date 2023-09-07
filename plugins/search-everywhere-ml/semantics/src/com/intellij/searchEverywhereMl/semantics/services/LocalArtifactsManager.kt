package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.io.Decompressor
import com.intellij.util.io.delete
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/* Service that manages the artifacts for local semantic models */
@Service
class LocalArtifactsManager {
  private val root = File(PathManager.getSystemPath())
    .resolve(SEMANTIC_SEARCH_RESOURCES_DIR)
    .resolve(MODEL_VERSION)
    .also { Files.createDirectories(it.toPath()) }
  private val modelArtifactsRoot = root.resolve(MODEL_ARTIFACTS_DIR)
  private val mutex = ReentrantLock()
  private var failNotificationShown = false

  init {
    root.parentFile.toPath().listDirectoryEntries().filter { it.name != MODEL_VERSION }.forEach { it.delete(recursively = true) }
  }

  fun getCustomRootDataLoader() = CustomRootDataLoader(modelArtifactsRoot.toPath())

  @RequiresBackgroundThread
  fun downloadArtifactsIfNecessary() = mutex.withLock {
    if (!checkArtifactsPresent()) {
      logger.debug { "Semantic search artifacts are not present, starting the download..." }
      val indicator = BackgroundableProcessIndicator(null, ARTIFACTS_DOWNLOAD_TASK_NAME, null, "", false)
      ProgressManager.getInstance().runProcess(this::downloadArtifacts, indicator)
      ApplicationManager.getApplication().invokeLater { Disposer.dispose(indicator) }
    }
  }

  fun getModelVersion(): String = MODEL_VERSION

  fun checkArtifactsPresent(): Boolean {
    return Files.isDirectory(modelArtifactsRoot.toPath()) && modelArtifactsRoot.toPath().listDirectoryEntries().isNotEmpty()
  }

  private fun downloadArtifacts() {
    Files.createDirectories(root.toPath())
    try {
      DownloadableFileService.getInstance().run {
        createDownloader(listOf(createFileDescription(MAVEN_ROOT, ARCHIVE_NAME)), ARTIFACTS_DOWNLOAD_TASK_NAME)
      }.download(root)
      logger.debug { "Downloaded archive with search artifacts into ${root.absoluteFile}" }

      modelArtifactsRoot.deleteRecursively()
      unpackArtifactsArchive(root.resolve(ARCHIVE_NAME), root)
      logger.debug { "Extracted model artifacts into the ${root.absoluteFile}" }
    }
    catch (e: IOException) {
      logger.warn("Failed to download semantic search artifacts")
      if (!failNotificationShown) {
        showDownloadErrorNotification()
        failNotificationShown = true
      }
    }
  }

  private fun unpackArtifactsArchive(archiveFile: File, destination: File) {
    Decompressor.Zip(archiveFile).overwrite(false).extract(destination)
    archiveFile.delete()
  }

  companion object {
    const val SEMANTIC_SEARCH_RESOURCES_DIR = "semantic-search"

    private val ARTIFACTS_DOWNLOAD_TASK_NAME = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.artifacts.download.name")

    private val MODEL_VERSION = Registry.stringValue("search.everywhere.ml.semantic.model.version")
    private val MAVEN_ROOT = "https://packages.jetbrains.team/maven/p/ml-search-everywhere/local-models/org/jetbrains/intellij/" +
                             "searcheverywhereMl/semantics/semantic-text-search/$MODEL_VERSION/semantic-text-search-$MODEL_VERSION.jar"

    private const val MODEL_ARTIFACTS_DIR = "models"
    private const val ARCHIVE_NAME = "semantic-text-search.jar"
    private const val NOTIFICATION_GROUP_ID = "Semantic search"

    private val logger by lazy { logger<LocalArtifactsManager>() }

    fun getInstance() = service<LocalArtifactsManager>()

    private fun showDownloadErrorNotification() {
      NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
        .createNotification(
          SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.notification.model.downloading.failed.title"),
          SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.notification.model.downloading.failed.content"),
          NotificationType.WARNING
        )
        .notify(null)
    }
  }
}