package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.io.Decompressor
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.listDirectoryEntries

/* Service that manages the artifacts for local semantic models */
@Service
class LocalArtifactsManager {
  private val root = File(PathManager.getSystemPath()).resolve(SEMANTIC_SEARCH_RESOURCES_DIR)
  private val modelArtifactsRoot = root.resolve(MODEL_ARTIFACTS_DIR)

  fun getCustomRootDataLoader() = CustomRootDataLoader(modelArtifactsRoot.toPath())

  fun downloadArtifactsIfNecessary() {
    if (!checkArtifactsPresent()) {
      ProgressManager.getInstance().run(object : Task.Backgroundable(null, ARTIFACTS_DOWNLOAD_TASK_NAME) {
        override fun run(indicator: ProgressIndicator) {
          downloadArtifacts()
        }
      })
    }
  }

  fun checkArtifactsPresent(): Boolean {
    return Files.isDirectory(modelArtifactsRoot.toPath()) && modelArtifactsRoot.toPath().listDirectoryEntries().isNotEmpty()
  }

  private fun downloadArtifacts() {
    Files.createDirectories(root.toPath())
    try {
      DownloadableFileService.getInstance().run {
        createDownloader(listOf(createFileDescription(MAVEN_ROOT, ARCHIVE_NAME)), ARTIFACTS_DOWNLOAD_TASK_NAME)
      }.download(root)

      modelArtifactsRoot.deleteRecursively()
      unpackArtifactsArchive(root.resolve(ARCHIVE_NAME), root)
    }
    catch (e: IOException) {
      showDownloadErrorNotification()
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