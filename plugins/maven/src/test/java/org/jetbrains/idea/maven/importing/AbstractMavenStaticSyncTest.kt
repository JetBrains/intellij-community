// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.replaceService
import org.jetbrains.idea.maven.project.preimport.MavenProjectStaticImporter
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.MavenIndexerWrapper
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.Assume
import java.io.File
import java.util.function.Predicate

abstract class AbstractMavenStaticSyncTest : MavenMultiVersionImportingTestCase() {

  private lateinit var disposable: Disposable
  override fun setUp() {
    super.setUp()
    Assume.assumeTrue(isWorkspaceImport)

    disposable = Disposer.newDisposable("Real maven protector for MavenSyncTest")
    val syncViewManager = object : SyncViewManager(project) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        noRealMavenAllowed()
      }
    }
    project.replaceService(SyncViewManager::class.java, syncViewManager, disposable)
    ApplicationManager.getApplication().replaceService(MavenServerManager::class.java, NoRealMavenServerManager(), disposable)


  }

  override fun tearDown() {
    runAll(
      { Disposer.dispose(disposable) },
      { super.tearDown() }
    )
  }

  override suspend fun importProjectsAsync(files: List<VirtualFile>) {
    val activity = ProjectImportCollector.IMPORT_ACTIVITY.started(project)
    try {
      MavenProjectStaticImporter.getInstance(project)
        .syncStatic(files, null, mavenImporterSettings, mavenGeneralSettings, true, activity)
    }
    finally {
      activity.finished()
    }
  }
}


class NoRealMavenServerManager : MavenServerManager {
  override fun dispose() {
  }

  override fun getAllConnectors(): MutableCollection<MavenServerConnector> {
    noRealMavenAllowed()
  }

  override fun restartMavenConnectors(project: Project, wait: Boolean, condition: Predicate<MavenServerConnector>) {
    noRealMavenAllowed()
  }

  override fun getConnectorBlocking(project: Project, workingDirectory: String): MavenServerConnector {
    noRealMavenAllowed()
  }

  override suspend fun getConnector(project: Project, workingDirectory: String): MavenServerConnector {
    noRealMavenAllowed()
  }

  override fun shutdownConnector(connector: MavenServerConnector, wait: Boolean): Boolean {
    noRealMavenAllowed()
  }

  override fun closeAllConnectorsAndWait() {
    noRealMavenAllowed()
  }

  override fun getMavenEventListener(): File {
    noRealMavenAllowed()
  }

  override fun createEmbedder(project: Project, alwaysOnline: Boolean, multiModuleProjectDirectory: String): MavenEmbedderWrapper {
    noRealMavenAllowed()
  }

  @Deprecated("Deprecated in Java")
  override fun createIndexer(project: Project): MavenIndexerWrapper {
    noRealMavenAllowed()
  }

  override fun createIndexer(): MavenIndexerWrapper {
    noRealMavenAllowed()
  }
}

private fun noRealMavenAllowed(): Nothing {
  throw IllegalStateException("No real maven in this test class!")
}
