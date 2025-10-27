// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.source

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.launchTracked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSyncListener

class MavenDownloadLibrarySourcesSyncListener : MavenSyncListener {

  override fun importFinished(project: Project, importedProjects: Collection<MavenProject>, newModules: List<Module>) {
    if (!MavenProjectsManager.getInstance(project).importingSettings.isDownloadSourcesAutomatically) {
      return
    }
    val projectManager = MavenProjectsManager.getInstanceIfCreated(project) ?: return
    MavenDownloadLibrarySourcesAfterSyncDebouncer.withDebounce(project) {
      projectManager.downloadArtifacts(projectManager.projects, null, true, false)
    }
  }

  @Service(Service.Level.PROJECT)
  private class MavenDownloadLibrarySourcesAfterSyncDebouncer(
    val cs: CoroutineScope,
  ) {
    private val mutex = Mutex()

    companion object {
      fun withDebounce(project: Project, action: suspend () -> Unit) {
        project.getService(MavenDownloadLibrarySourcesAfterSyncDebouncer::class.java)
          .withDebounce { action() }
      }
    }

    private fun withDebounce(action: suspend () -> Unit) {
      if (mutex.tryLock()) {
        cs.launchTracked {
          try {
            action()
          }
          finally {
            mutex.unlock()
          }
        }
      }
    }
  }
}