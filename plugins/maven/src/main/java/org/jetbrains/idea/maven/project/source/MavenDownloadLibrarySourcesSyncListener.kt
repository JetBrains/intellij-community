// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.source

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole.RescheduledMavenDownloadJobException
import org.jetbrains.idea.maven.project.MavenDownloadSourcesRequest
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSyncListener
import kotlin.time.Duration.Companion.seconds

class MavenDownloadLibrarySourcesSyncListener : MavenSyncListener {

  override fun importFinished(project: Project, importedProjects: Collection<MavenProject>, newModules: List<Module>) {
    if (!MavenProjectsManager.getInstance(project).importingSettings.isDownloadSourcesAutomatically) {
      return
    }
    val projectManager = MavenProjectsManager.getInstanceIfCreated(project) ?: return
    MavenDownloadLibrarySourcesAfterSyncTaskDispatcher.dispatchSingletonJob(project) {
      projectManager.downloadArtifacts(
        MavenDownloadSourcesRequest.builder()
          .forProjects(projectManager.projects)
          .forAllArtifacts()
          .withSources()
          .withProgressDelay(1.seconds)
          .withProgressVisibility(false)
          .build()
      )
    }
  }

  @Service(Service.Level.PROJECT)
  private class MavenDownloadLibrarySourcesAfterSyncTaskDispatcher(
    val cs: CoroutineScope,
  ) {
    // This mutex is required to make sure that the previous job is canceled before a new one is submitted.
    // It prevents several jobs to be executed at the same time
    private val mutex = Mutex()
    private var job: Job? = null

    companion object {
      fun dispatchSingletonJob(project: Project, action: suspend () -> Unit) {
        project.getService(MavenDownloadLibrarySourcesAfterSyncTaskDispatcher::class.java)
          .dispatchSingletonJob { action() }
      }
    }

    private fun dispatchSingletonJob(action: suspend () -> Unit) = cs.launch {
      mutex.withLock {
        job?.cancel(
          RescheduledMavenDownloadJobException("A new job was submitted for execution. The previous one should be canceled.")
        )
        // we should wait for the job to be canceled before starting a new one
        job?.join()
        val newJob = launch {
          action()
        }
        job = newJob
      }
    }
  }
}