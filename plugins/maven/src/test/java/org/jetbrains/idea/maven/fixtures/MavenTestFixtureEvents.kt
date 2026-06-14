// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.MavenImportingTestFixture
import com.intellij.maven.testFramework.fixtures.awaitConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerDownloadListener
import org.jetbrains.idea.maven.utils.MavenLog
import org.junit.Assert.assertTrue
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// Helpers that run an [action] and assert that the expected import / index / download events fired.

@RequiresBackgroundThread
suspend fun MavenImportingTestFixture.waitForImportWithinTimeout(action: suspend () -> Unit) {
  val importStarted = AtomicBoolean(false)
  val importFinished = AtomicBoolean(false)
  val pluginResolutionFinished = AtomicBoolean(true)
  val artifactDownloadingFinished = AtomicBoolean(true)
  project.messageBus.connect(disposable)
    .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
      override fun importStarted() {
        importStarted.set(true)
      }

      override fun importFinished(importedProjects: MutableCollection<MavenProject>, newModules: MutableList<Module>) {
        if (importStarted.get()) {
          importFinished.set(true)
        }
      }

      override fun pluginResolutionStarted() {
        pluginResolutionFinished.set(false)
      }

      override fun pluginResolutionFinished() {
        pluginResolutionFinished.set(true)
      }

      override fun artifactDownloadingStarted() {
        artifactDownloadingFinished.set(false)
      }

      override fun artifactDownloadingFinished() {
        artifactDownloadingFinished.set(true)
      }
    })

  action()

  awaitConfiguration()

  assertTrue("Import failed: start", importStarted.get())
  assertTrue("Import failed: finish", importFinished.get())
  assertTrue("Import failed: plugins", pluginResolutionFinished.get())
  assertTrue("Import failed: artifacts", artifactDownloadingFinished.get())
}

suspend fun MavenDomTestFixture.runAndExpectPluginIndexEvents(expectedArtifactIds: Set<String>, action: suspend () -> Unit) {
  val artifactIdsToIndex: MutableSet<String> = ConcurrentHashMap.newKeySet()
  artifactIdsToIndex.addAll(expectedArtifactIds)

  ApplicationManager.getApplication().messageBus.connect(disposable)
    .subscribe(MavenIndicesManager.INDEXER_TOPIC, object : MavenIndicesManager.MavenIndexerListener {
      override fun gavIndexUpdated(repo: MavenRepositoryInfo, added: Set<File>, failedToAdd: Set<File>) {
        artifactIdsToIndex.removeIf { artifactId: String ->
          added.any { file: File -> file.path.contains(artifactId) }
        }
      }
    })

  action()

  awaitConfiguration()

  assertTrue("Maven plugins are not indexed in time: " + java.lang.String.join(", ", artifactIdsToIndex), artifactIdsToIndex.isEmpty())
}

suspend fun MavenDomTestFixture.runAndExpectArtifactDownloadEvents(expectedGroupId: String, expectedArtifactIds: Set<String>, action: suspend () -> Unit) {
  val groupFolder = expectedGroupId.replace('.', '/')
  val actualEvents: MutableSet<String> = ConcurrentHashMap.newKeySet()
  val downloadListener = MavenServerDownloadListener { file ->
    val absolutePath = FileUtilRt.toSystemIndependentName(file.absolutePath)
    if (absolutePath.contains(groupFolder) && absolutePath.endsWith("jar")) {
      val artifactId = absolutePath.substringAfter(groupFolder).split("/")[1]
      if (expectedArtifactIds.contains(artifactId)) {
        MavenLog.LOG.warn("Artifact $artifactId is downloaded")
        actualEvents.add(artifactId)
      }
    }
  }

  ApplicationManager.getApplication().messageBus.connect(disposable)
    .subscribe(MavenServerConnector.DOWNLOAD_LISTENER_TOPIC, downloadListener)

  action()

  awaitConfiguration()

  val extraDownloaded = actualEvents - expectedArtifactIds
  assertEmpty("Unexpected artifacts downloaded", extraDownloaded)

  val notDownloaded = expectedArtifactIds - actualEvents
  assertEmpty("Artifacts not downloaded", notDownloaded)
}
