package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
  private val root = File(PathManager.getSystemPath()).resolve("semantic-text-search").also { Files.createDirectories(it.toPath()) }

  private val artifactRoot = root.resolve("models")

  fun getCustomRootDataLoader() = CustomRootDataLoader(artifactRoot.toPath())

  fun tryPrepareArtifacts() {
    if (!checkArtifactsPresent()) {
      prepareArtifacts()
    }
  }

  fun checkArtifactsPresent(): Boolean {
    return Files.isDirectory(artifactRoot.toPath()) && artifactRoot.toPath().listDirectoryEntries().isNotEmpty()
  }

  private fun prepareArtifacts() {
    try {
      service<DownloadableFileService>().run {
        createDownloader(
          listOf(createFileDescription(MAVEN_ROOT, ARCHIVE_NAME)),
          SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.models.download.name")
        )
      }.download(root)

      unpackArtifactsArchive(root.resolve(ARCHIVE_NAME), artifactRoot)
    }
    catch (e: IOException) {
      showDownloadErrorNotification()
    }
  }

  private fun unpackArtifactsArchive(archiveFile: File, destination: File) {
    destination.deleteRecursively()
    Decompressor.Zip(archiveFile).extract(destination)
    check(destination.isDirectory)
    archiveFile.delete()
  }

  companion object {
    private val MODEL_VERSION = Registry.stringValue("search.everywhere.ml.semantic.model.version")
    private val MAVEN_ROOT = "https://packages.jetbrains.team/maven/p/ml-search-everywhere/local-models/org/jetbrains/intellij/" +
                             "searcheverywhereMl/semantics/semantic-text-search/$MODEL_VERSION/semantic-text-search-$MODEL_VERSION.jar"
    const val ARCHIVE_NAME = "semantic-text-search.jar"

    private const val NOTIFICATION_GROUP_ID = "Semantic search notifications"

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